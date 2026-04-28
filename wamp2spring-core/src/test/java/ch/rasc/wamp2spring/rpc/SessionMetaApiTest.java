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

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;

public class SessionMetaApiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void exposesSessionMetaProcedures() throws WampException {
		SessionRegistry sessionRegistry = new SessionRegistry();
		sessionRegistry.add(new SessionDetail(101L, "ws-1", "anna", "admin", "ticket", "static", 1L));
		sessionRegistry.add(new SessionDetail(202L, "ws-2", "bob", "user", "transport", "transport", 2L));

		SessionMetaApi api = new SessionMetaApi(sessionRegistry, new WampPublisher(Mockito.mock(MessageChannel.class)),
				Mockito.mock(MessageChannel.class));

		WampResult countResult = api.count(new CallMessage(1L, SessionMetaApi.COUNT, List.of()));
		WampResult filteredCountResult = api
			.count(new CallMessage(2L, SessionMetaApi.COUNT, List.of(List.of("admin"))));
		WampResult listResult = api.list(new CallMessage(3L, SessionMetaApi.LIST, List.of(List.of("user", "admin"))));
		WampResult getResult = api.get(new CallMessage(4L, SessionMetaApi.GET, List.of(101L)));
		List<Object> listResults = Objects.requireNonNull(listResult.getResults());
		List<Object> getResults = Objects.requireNonNull(getResult.getResults());

		assertThat(countResult.getResults()).containsExactly(2);
		assertThat(filteredCountResult.getResults()).containsExactly(1);
		assertThat((List<Long>) listResults.get(0)).containsExactly(101L, 202L);

		Map<String, Object> detail = (Map<String, Object>) getResults.get(0);
		assertThat(detail.get("session")).isEqualTo(101L);
		assertThat(detail.get("authid")).isEqualTo("anna");
		assertThat(detail.get("authrole")).isEqualTo("admin");
		assertThat(detail.get("authmethod")).isEqualTo("ticket");
		assertThat(detail.get("authprovider")).isEqualTo("static");
		assertThat(((Map<String, Object>) Objects.requireNonNull(detail.get("transport"))).get("websocket_session_id"))
			.isEqualTo("ws-1");

		assertThatThrownBy(() -> api.get(new CallMessage(5L, SessionMetaApi.GET, List.of(999L))))
			.isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.NO_SUCH_SESSION.getExternalValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void publishesSessionLifecycleTopics() {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(brokerChannel.send(ArgumentMatchers.any(PublishMessage.class))).thenReturn(true);
		MessageChannel clientOutboundChannel = Mockito.mock(MessageChannel.class);

		SessionRegistry sessionRegistry = new SessionRegistry();
		SessionMetaApi api = new SessionMetaApi(sessionRegistry, new WampPublisher(brokerChannel),
				clientOutboundChannel);

		api.onSessionEstablished(
				new WampSessionEstablishedEvent(welcomeMessage(101L, "ws-1", new TestPrincipal("anna", "ROLE_admin"))));
		api.onSessionLeft(new WampDisconnectEvent(101L, "ws-1", null));

		ArgumentCaptor<PublishMessage> publishCaptor = ArgumentCaptor.forClass(PublishMessage.class);
		Mockito.verify(brokerChannel, Mockito.times(2)).send(publishCaptor.capture());
		List<PublishMessage> publishedMessages = publishCaptor.getAllValues();
		List<Object> joinArguments = Objects.requireNonNull(publishedMessages.get(0).getArguments());

		assertThat(publishedMessages.get(0).getTopic()).isEqualTo(SessionMetaApi.ON_JOIN);
		Map<String, Object> joinDetail = (Map<String, Object>) joinArguments.get(0);
		assertThat(joinDetail.get("session")).isEqualTo(101L);
		assertThat(joinDetail.get("authid")).isEqualTo("anna");
		assertThat(joinDetail.get("authrole")).isEqualTo("admin");
		assertThat(joinDetail.get("authmethod")).isEqualTo("transport");
		assertThat(joinDetail.get("authprovider")).isEqualTo("transport");

		assertThat(publishedMessages.get(1).getTopic()).isEqualTo(SessionMetaApi.ON_LEAVE);
		assertThat(publishedMessages.get(1).getArguments()).containsExactly(101L, "anna", "admin");
		assertThat(sessionRegistry.get(101L)).isNull();
	}

	@Test
	public void killsSessionsWithExpectedResultsAndGoodbyeMessages() throws WampException {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		MessageChannel clientOutboundChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(clientOutboundChannel.send(ArgumentMatchers.any(GoodbyeMessage.class))).thenReturn(true);

		SessionRegistry sessionRegistry = new SessionRegistry();
		sessionRegistry.add(new SessionDetail(101L, "ws-1", "anna", "admin", 1L));
		sessionRegistry.add(new SessionDetail(202L, "ws-2", "anna", "user", 2L));
		sessionRegistry.add(new SessionDetail(303L, "ws-3", "bob", "admin", 3L));
		sessionRegistry.add(new SessionDetail(404L, "ws-4", "carl", "user", 4L));

		SessionMetaApi api = new SessionMetaApi(sessionRegistry, new WampPublisher(brokerChannel),
				clientOutboundChannel);

		CallMessage killByAuthId = new CallMessage(10L, SessionMetaApi.KILL_BY_AUTHID, List.of("anna"),
				Map.of("reason", "com.myapp.shutdown", "message", "bye"), false);
		killByAuthId.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		WampResult killByAuthIdResult = api.killByAuthId(killByAuthId);
		assertThat(killByAuthIdResult.getResults()).containsExactly(List.of(202L));

		CallMessage killByAuthRole = new CallMessage(11L, SessionMetaApi.KILL_BY_AUTHROLE, List.of("admin"));
		killByAuthRole.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		WampResult killByAuthRoleResult = api.killByAuthRole(killByAuthRole);
		assertThat(killByAuthRoleResult.getResults()).containsExactly(1);

		CallMessage killAll = new CallMessage(12L, SessionMetaApi.KILL_ALL);
		killAll.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		WampResult killAllResult = api.killAll(killAll);
		assertThat(killAllResult.getResults()).containsExactly(3);

		CallMessage killSingle = new CallMessage(13L, SessionMetaApi.KILL, List.of(404L));
		killSingle.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		WampResult killSingleResult = api.kill(killSingle);
		assertThat(killSingleResult.getResults()).isNull();

		ArgumentCaptor<GoodbyeMessage> goodbyeCaptor = ArgumentCaptor.forClass(GoodbyeMessage.class);
		Mockito.verify(clientOutboundChannel, Mockito.times(6)).send(goodbyeCaptor.capture());
		assertThat(goodbyeCaptor.getAllValues().get(0).getReason()).isEqualTo("com.myapp.shutdown");
		assertThat(goodbyeCaptor.getAllValues().get(0).getMessage()).isEqualTo("bye");
		assertThat(goodbyeCaptor.getAllValues().get(0).getWebSocketSessionId()).isEqualTo("ws-2");
		assertThat(goodbyeCaptor.getAllValues().get(1).getReason())
			.isEqualTo(WampError.CLOSE_KILLED.getExternalValue());
	}

	@Test
	public void rejectsInvalidReasonAndOwnSessionKill() {
		SessionRegistry sessionRegistry = new SessionRegistry();
		sessionRegistry.add(new SessionDetail(101L, "ws-1", "anna", "admin", 1L));
		MessageChannel clientOutboundChannel = Mockito.mock(MessageChannel.class);
		SessionMetaApi api = new SessionMetaApi(sessionRegistry, new WampPublisher(Mockito.mock(MessageChannel.class)),
				clientOutboundChannel);

		CallMessage invalidReason = new CallMessage(20L, SessionMetaApi.KILL_ALL, null, Map.of("reason", "not a uri"),
				false);
		invalidReason.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		assertThatThrownBy(() -> api.killAll(invalidReason)).isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.INVALID_URI.getExternalValue());

		CallMessage ownKill = new CallMessage(21L, SessionMetaApi.KILL, List.of(101L));
		ownKill.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		assertThatThrownBy(() -> api.kill(ownKill)).isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.NO_SUCH_SESSION.getExternalValue());
	}

	private static WelcomeMessage welcomeMessage(long sessionId, String webSocketSessionId, Principal principal) {
		WelcomeMessage message = new WelcomeMessage(sessionId, List.of(new WampRole("dealer")));
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, sessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		message.setHeader(WampMessageHeader.PRINCIPAL, principal);
		message.setHeader(WampMessageHeader.AUTH_METHOD, "transport");
		message.setHeader(WampMessageHeader.AUTH_PROVIDER, "transport");
		return message;
	}

	private static final class TestPrincipal implements Principal {

		private final String name;

		private final List<TestAuthority> authorities;

		private TestPrincipal(String name, String... authorities) {
			this.name = name;
			this.authorities = java.util.Arrays.stream(authorities).map(TestAuthority::new).toList();
		}

		@Override
		public String getName() {
			return this.name;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public List<TestAuthority> getAuthorities() {
			return this.authorities;
		}

	}

	private static final class TestAuthority {

		private final String authority;

		private TestAuthority(String authority) {
			this.authority = authority;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public String getAuthority() {
			return this.authority;
		}

	}

}