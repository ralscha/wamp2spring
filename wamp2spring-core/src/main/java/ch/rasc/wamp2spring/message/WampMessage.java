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
package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

public abstract class WampMessage implements Message<Object> {

	private final static Object EMPTY_OBJECT = new Object();

	private final MutableMessageHeaders messageHeaders = new MutableMessageHeaders();

	private final int code;

	WampMessage(int code) {
		this.code = code;
		setHeader(WampMessageHeader.WAMP_MESSAGE_CODE, code);
	}

	/**
	 * Returns the WAMP message code
	 */
	public int getCode() {
		return this.code;
	}

	/**
	 * Overwrites or inserts a new header into the message. Null values are ignored
	 * @param header the header
	 * @param value an arbitrary value. null values are ignored
	 */
	public void setHeader(WampMessageHeader header, @Nullable Object value) {
		if (value != null) {
			this.messageHeaders.getRawHeaders().put(header.name(), value);
		}
	}

	@SuppressWarnings({ "unchecked", "TypeParameterUnusedInFormals" })
	@Nullable public <T> T getHeader(WampMessageHeader header) {
		return (T) this.messageHeaders.get(header.name());
	}

	/**
	 * Returns the WebSocket session id that Spring assigns to each session.
	 */
	@Nullable public String getWebSocketSessionId() {
		return getHeader(WampMessageHeader.WEBSOCKET_SESSION_ID);
	}

	/**
	 * Returns the value of the principal header
	 */
	@Nullable public Principal getPrincipal() {
		return getHeader(WampMessageHeader.PRINCIPAL);
	}

	@Nullable public String getAuthId() {
		Principal principal = getPrincipal();
		if (principal == null) {
			return null;
		}

		String name = principal.getName();
		if (name == null || name.isBlank()) {
			return null;
		}
		return name;
	}

	@Nullable public String getAuthRole() {
		Principal principal = getPrincipal();
		if (principal == null) {
			return null;
		}

		Object authorities = invokeNoArgMethod(principal, "getAuthorities");
		String fallbackAuthority = null;
		for (Object authority : asIterable(authorities)) {
			String authorityValue = extractAuthority(authority);
			if (authorityValue == null || authorityValue.isBlank()) {
				continue;
			}
			if (authorityValue.startsWith("ROLE_")) {
				return authorityValue.substring(5);
			}
			if (fallbackAuthority == null) {
				fallbackAuthority = authorityValue;
			}
		}

		return fallbackAuthority;
	}

	public String getAuthMethod() {
		String authMethod = getHeader(WampMessageHeader.AUTH_METHOD);
		if (authMethod != null) {
			return authMethod;
		}
		return getPrincipal() == null ? "anonymous" : "transport";
	}

	public String getAuthProvider() {
		String authProvider = getHeader(WampMessageHeader.AUTH_PROVIDER);
		if (authProvider != null) {
			return authProvider;
		}
		return getPrincipal() == null ? "static" : "transport";
	}

	@Nullable public Number getTrustLevel() {
		return getHeader(WampMessageHeader.TRUSTLEVEL);
	}

	/**
	 * Returns the WAMP session id that this library assigns to each session.
	 */
	@Nullable public Long getWampSessionId() {
		return getHeader(WampMessageHeader.WAMP_SESSION_ID);
	}

	@Nullable public String getRealm() {
		return getHeader(WampMessageHeader.WAMP_REALM);
	}

	@Nullable public List<WampRole> getPeerRoles() {
		return getHeader(WampMessageHeader.WAMP_PEER_ROLES);
	}

	protected void setReceiver(WampMessage message) {
		setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, message.getWebSocketSessionId());
		setHeader(WampMessageHeader.PRINCIPAL, message.getPrincipal());
		setHeader(WampMessageHeader.WAMP_SESSION_ID, message.getWampSessionId());
		setHeader(WampMessageHeader.WAMP_REALM, message.getRealm());
		setHeader(WampMessageHeader.WAMP_PEER_ROLES, message.getPeerRoles());
		setHeader(WampMessageHeader.AUTH_METHOD, message.getAuthMethod());
		setHeader(WampMessageHeader.AUTH_PROVIDER, message.getAuthProvider());
		setHeader(WampMessageHeader.TRUSTLEVEL, message.getTrustLevel());
	}

	protected void setReceiverWebSocketSessionId(String receiverWebSocketSessionId) {
		setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, receiverWebSocketSessionId);
	}

	@Override
	public Object getPayload() {
		return EMPTY_OBJECT;
	}

	@Override
	public MessageHeaders getHeaders() {
		return this.messageHeaders;
	}

	public abstract void serialize(JsonGenerator generator) throws IOException;

	@Nullable private static Object invokeNoArgMethod(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		}
		catch (Exception ex) {
			try {
				Method declaredMethod = target.getClass().getDeclaredMethod(methodName);
				declaredMethod.setAccessible(true);
				return declaredMethod.invoke(target);
			}
			catch (Exception ignored) {
				return null;
			}
		}
	}

	private static Iterable<?> asIterable(@Nullable Object value) {
		if (value instanceof Iterable<?>) {
			return (Iterable<?>) value;
		}
		if (value != null && value.getClass().isArray()) {
			java.util.ArrayList<Object> values = new java.util.ArrayList<>(Array.getLength(value));
			for (int i = 0; i < Array.getLength(value); i++) {
				values.add(Array.get(value, i));
			}
			return values;
		}
		return List.of();
	}

	@Nullable private static String extractAuthority(Object authority) {
		if (authority == null) {
			return null;
		}

		Object extractedValue = invokeNoArgMethod(authority, "getAuthority");
		if (extractedValue == null) {
			return authority.toString();
		}

		return extractedValue.toString();
	}

	@SuppressWarnings({ "unchecked", "TypeParameterUnusedInFormals" })
	public static <T extends WampMessage> T deserialize(ObjectMapper objectMapper, byte[] json) throws IOException {

		try (JsonParser jp = objectMapper.createParser(json)) {
			if (jp.nextToken() != JsonToken.START_ARRAY) {
				throw new IOException("Not a JSON array");
			}
			if (jp.nextToken() != JsonToken.VALUE_NUMBER_INT) {
				throw new IOException("Wrong message format");
			}

			int code = jp.getValueAsInt();

			return switch (code) {
				case HelloMessage.CODE -> (T) HelloMessage.deserialize(jp);
				case WelcomeMessage.CODE -> (T) WelcomeMessage.deserialize(jp);
				case AbortMessage.CODE -> (T) AbortMessage.deserialize(jp);
				case ChallengeMessage.CODE -> (T) ChallengeMessage.deserialize(jp);
				case AuthenticateMessage.CODE -> (T) AuthenticateMessage.deserialize(jp);
				case CancelMessage.CODE -> (T) CancelMessage.deserialize(jp);
				case GoodbyeMessage.CODE -> (T) GoodbyeMessage.deserialize(jp);
				case ErrorMessage.CODE -> (T) ErrorMessage.deserialize(jp);
				case PublishMessage.CODE -> (T) PublishMessage.deserialize(jp);
				case PublishedMessage.CODE -> (T) PublishedMessage.deserialize(jp);
				case SubscribeMessage.CODE -> (T) SubscribeMessage.deserialize(jp);
				case SubscribedMessage.CODE -> (T) SubscribedMessage.deserialize(jp);
				case UnsubscribeMessage.CODE -> (T) UnsubscribeMessage.deserialize(jp);
				case UnsubscribedMessage.CODE -> (T) UnsubscribedMessage.deserialize(jp);
				case EventMessage.CODE -> (T) EventMessage.deserialize(jp);
				case CallMessage.CODE -> (T) CallMessage.deserialize(jp);
				case ResultMessage.CODE -> (T) ResultMessage.deserialize(jp);
				case RegisterMessage.CODE -> (T) RegisterMessage.deserialize(jp);
				case RegisteredMessage.CODE -> (T) RegisteredMessage.deserialize(jp);
				case UnregisterMessage.CODE -> (T) UnregisterMessage.deserialize(jp);
				case UnregisteredMessage.CODE -> (T) UnregisteredMessage.deserialize(jp);
				case InterruptMessage.CODE -> (T) InterruptMessage.deserialize(jp);
				case YieldMessage.CODE -> (T) YieldMessage.deserialize(jp);
				case InvocationMessage.CODE -> (T) InvocationMessage.deserialize(jp);
				default -> throw new IOException("Unknown message code: " + code);
			};

		}

	}

	@SuppressWarnings("serial")
	private static class MutableMessageHeaders extends MessageHeaders {

		MutableMessageHeaders() {
			super(null, ID_VALUE_NONE, -1L);
		}

		@Override
		public Map<String, Object> getRawHeaders() {
			return super.getRawHeaders();
		}

	}

}
