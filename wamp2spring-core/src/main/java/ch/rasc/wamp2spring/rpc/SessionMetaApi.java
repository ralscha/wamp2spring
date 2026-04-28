/*
 * Copyright the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.wamp2spring.rpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;

public class SessionMetaApi {

	static final String COUNT = "wamp.session.count";

	static final String LIST = "wamp.session.list";

	static final String GET = "wamp.session.get";

	static final String KILL = "wamp.session.kill";

	static final String KILL_BY_AUTHID = "wamp.session.kill_by_authid";

	static final String KILL_BY_AUTHROLE = "wamp.session.kill_by_authrole";

	static final String KILL_ALL = "wamp.session.kill_all";

	static final String ON_JOIN = "wamp.session.on_join";

	static final String ON_LEAVE = "wamp.session.on_leave";

	private static final Pattern URI_PATTERN = Pattern.compile("^([^\\s.#]+\\.)*([^\\s.#]+)$");

	private final SessionRegistry sessionRegistry;

	private final WampPublisher wampPublisher;

	private final MessageChannel clientOutboundChannel;

	public SessionMetaApi(SessionRegistry sessionRegistry, WampPublisher wampPublisher,
			MessageChannel clientOutboundChannel) {
		this.sessionRegistry = sessionRegistry;
		this.wampPublisher = wampPublisher;
		this.clientOutboundChannel = clientOutboundChannel;
	}

	@WampProcedure(COUNT)
	public WampResult count(CallMessage callMessage) {
		return WampResult.create(this.sessionRegistry.count(callMessage.getRealm(), authRoleFilter(callMessage)));
	}

	@WampProcedure(LIST)
	public WampResult list(CallMessage callMessage) {
		return WampResult.create(this.sessionRegistry.list(callMessage.getRealm(), authRoleFilter(callMessage)));
	}

	@WampProcedure(GET)
	public WampResult get(CallMessage callMessage) throws WampException {
		long sessionId = longArgument(callMessage, 0);
		SessionDetail detail = this.sessionRegistry.get(sessionId);
		if (detail == null || !sameRealm(callMessage, detail)) {
			throw noSuchSession();
		}
		return WampResult.create(toMetaDetail(detail));
	}

	@WampProcedure(KILL)
	public WampResult kill(CallMessage callMessage) throws WampException {
		long callerSessionId = callerSessionId(callMessage);
		long sessionId = longArgument(callMessage, 0);
		if (sessionId == callerSessionId) {
			throw noSuchSession();
		}

		SessionDetail detail = this.sessionRegistry.get(sessionId);
		if (detail == null || !sameRealm(callMessage, detail)) {
			throw noSuchSession();
		}

		CloseDetails closeDetails = closeDetails(callMessage);
		terminate(detail, closeDetails.reason(), closeDetails.message());
		return new WampResult();
	}

	@WampProcedure(KILL_BY_AUTHID)
	public WampResult killByAuthId(CallMessage callMessage) throws WampException {
		String authId = stringArgument(callMessage, 0);
		long callerSessionId = callerSessionId(callMessage);
		CloseDetails closeDetails = closeDetails(callMessage);

		List<Long> killedSessions = new ArrayList<>();
		for (SessionDetail detail : this.sessionRegistry.findByAuthId(callMessage.getRealm(), authId)) {
			if (detail.getSessionId() == callerSessionId) {
				continue;
			}
			terminate(detail, closeDetails.reason(), closeDetails.message());
			killedSessions.add(detail.getSessionId());
		}
		return WampResult.create(killedSessions);
	}

	@WampProcedure(KILL_BY_AUTHROLE)
	public WampResult killByAuthRole(CallMessage callMessage) throws WampException {
		String authRole = stringArgument(callMessage, 0);
		long callerSessionId = callerSessionId(callMessage);
		CloseDetails closeDetails = closeDetails(callMessage);

		int count = 0;
		for (SessionDetail detail : this.sessionRegistry.findByAuthRole(callMessage.getRealm(), authRole)) {
			if (detail.getSessionId() == callerSessionId) {
				continue;
			}
			terminate(detail, closeDetails.reason(), closeDetails.message());
			count++;
		}
		return WampResult.create(count);
	}

	@WampProcedure(KILL_ALL)
	public WampResult killAll(CallMessage callMessage) throws WampException {
		long callerSessionId = callerSessionId(callMessage);
		CloseDetails closeDetails = closeDetails(callMessage);

		int count = 0;
		for (SessionDetail detail : this.sessionRegistry.listDetails(callMessage.getRealm())) {
			if (detail.getSessionId() == callerSessionId) {
				continue;
			}
			terminate(detail, closeDetails.reason(), closeDetails.message());
			count++;
		}
		return WampResult.create(count);
	}

	@EventListener
	public void onSessionEstablished(WampSessionEstablishedEvent event) {
		Long sessionId = event.getWampSessionId();
		if (sessionId == null) {
			return;
		}

		SessionDetail detail = new SessionDetail(sessionId, event.getWebSocketSessionId(), event.getRealm(),
				event.getAuthId(), event.getAuthRole(), event.getAuthMethod(), event.getAuthProvider(),
				System.currentTimeMillis());
		this.sessionRegistry.add(detail);
		publishEvent(detail.getRealm(), ON_JOIN, toMetaDetail(detail));
	}

	@EventListener
	public void onSessionLeft(WampDisconnectEvent event) {
		Long sessionId = event.getWampSessionId();
		if (sessionId == null) {
			return;
		}

		SessionDetail detail = this.sessionRegistry.remove(sessionId);
		if (detail == null) {
			detail = new SessionDetail(sessionId, event.getWebSocketSessionId(), event.getRealm(), event.getAuthId(),
					event.getAuthRole(), event.getAuthMethod(), event.getAuthProvider(), System.currentTimeMillis());
		}
		publishEvent(detail.getRealm(), ON_LEAVE, detail.getSessionId(), detail.getAuthId(), detail.getAuthRole());
	}

	private void publishEvent(@Nullable String realm, String topic, @Nullable Object... arguments) {
		List<Object> payload = new ArrayList<>(arguments.length);
		Collections.addAll(payload, arguments);
		PublishMessage publishMessage = this.wampPublisher.publishMessageBuilder(topic).arguments(payload).build();
		publishMessage.setHeader(WampMessageHeader.WAMP_REALM, realm);
		this.wampPublisher.publish(publishMessage);
	}

	private void terminate(SessionDetail detail, String reason, @Nullable String message) {
		GoodbyeMessage goodbyeMessage = new GoodbyeMessage(reason, message);
		goodbyeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, detail.getWebSocketSessionId());
		goodbyeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, detail.getSessionId());
		goodbyeMessage.setHeader(WampMessageHeader.WAMP_REALM, detail.getRealm());
		this.clientOutboundChannel.send(goodbyeMessage);
	}

	private static boolean sameRealm(CallMessage callMessage, SessionDetail detail) {
		return java.util.Objects.equals(callMessage.getRealm(), detail.getRealm());
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static List<String> authRoleFilter(CallMessage callMessage) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.isEmpty() || arguments.get(0) == null) {
			return null;
		}
		return (List<String>) arguments.get(0);
	}

	private static long longArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		if (value == null) {
			throw new IllegalArgumentException("missing call argument");
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}

	private static String stringArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		if (value == null) {
			throw new IllegalArgumentException("missing call argument");
		}
		return value.toString();
	}

	private static long callerSessionId(CallMessage callMessage) {
		Long sessionId = callMessage.getWampSessionId();
		if (sessionId == null) {
			throw new IllegalArgumentException("missing caller session id");
		}
		return sessionId;
	}

	private static CloseDetails closeDetails(CallMessage callMessage) throws WampException {
		Map<String, Object> argumentsKw = callMessage.getArgumentsKw();
		String reason = WampError.CLOSE_KILLED.getExternalValue();
		String message = null;
		if (argumentsKw != null) {
			Object reasonValue = argumentsKw.get("reason");
			if (reasonValue != null) {
				reason = reasonValue.toString();
				validateReason(reason);
			}
			Object messageValue = argumentsKw.get("message");
			if (messageValue != null) {
				message = messageValue.toString();
			}
		}
		return new CloseDetails(reason, message);
	}

	private static void validateReason(String reason) throws WampException {
		if (!URI_PATTERN.matcher(reason).matches()) {
			throw new WampException.Builder().build(WampError.INVALID_URI.getExternalValue());
		}
	}

	private static Object argument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			throw new IllegalArgumentException("missing call argument");
		}
		return arguments.get(index);
	}

	private static WampException noSuchSession() {
		return new WampException.Builder().build(WampError.NO_SUCH_SESSION.getExternalValue());
	}

	private static Map<String, Object> toMetaDetail(SessionDetail detail) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("session", detail.getSessionId());
		result.put("realm", detail.getRealm());
		result.put("authid", detail.getAuthId());
		result.put("authrole", detail.getAuthRole());
		result.put("authmethod", detail.getAuthMethod());
		result.put("authprovider", detail.getAuthProvider());

		Map<String, Object> transport = new LinkedHashMap<>();
		transport.put("websocket_session_id", detail.getWebSocketSessionId());
		result.put("transport", transport);
		return result;
	}

	private record CloseDetails(String reason, @Nullable String message) {
		// record holder
	}

}