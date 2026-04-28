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
package ch.rasc.wamp2spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.auth.TicketWampAuthenticationProvider;
import ch.rasc.wamp2spring.auth.WampAuthentication;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.ChallengeMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.servlet.EnableServletWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.CompletableFutureWebSocketHandler;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConnectionTest.Config.class)
public class ConnectionTest extends BaseWampTest {

	@Test
	public void secondHelloMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			// send hello message after session is established — server must reply
			// with ABORT (protocol_violation) and close the connection
			AbortMessage abortMessage = wc.sendMessageWithResult(new HelloMessage("theRealm", List.of()));
			assertThat(abortMessage.getReason()).isEqualTo(WampError.PROTOCOL_VIOLATION.getExternalValue());
			// wait for the server to finish closing the connection
			wc.waitForNothing();
			try {
				wc.sendMessage(new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	public void sendAbortMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			wc.sendMessage(new AbortMessage(WampError.NETWORK_FAILURE));

			wc.waitForNothing();
			try {
				wc.sendMessage(new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	public void ticketAuthenticationSuccessTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("publisher"));
			roles.add(new WampRole("subscriber"));
			roles.add(new WampRole("caller"));

			HelloMessage helloMessage = new HelloMessage("realm", roles, List.of("ticket"), "alice",
					Map.of("tenant", "demo"));
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("ticket");
			assertThat(challengeMessage.getExtra()).containsEntry("challenge", "ticket");

			result.reset();
			sendMessage(DataFormat.JSON, wsSession,
					new AuthenticateMessage("demo-ticket", Map.of("channel_binding", "none")));

			WelcomeMessage welcomeMessage = result.getWelcomeMessage();
			assertThat(welcomeMessage.getAuthId()).isEqualTo("alice");
			assertThat(welcomeMessage.getAuthRole()).isEqualTo("admin");
			assertThat(welcomeMessage.getAuthMethod()).isEqualTo("ticket");
			assertThat(welcomeMessage.getAuthProvider()).isEqualTo("static");
		}
	}

	@Test
	public void ticketAuthenticationFailureTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			HelloMessage helloMessage = new HelloMessage("realm",
					List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")),
					List.of("ticket"), "alice", null);
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("ticket");

			result.reset();
			sendMessage(DataFormat.JSON, wsSession, new AuthenticateMessage("wrong-ticket", null));

			AbortMessage abortMessage = (AbortMessage) result.getWampMessage();
			assertThat(abortMessage.getReason()).isEqualTo(WampError.NOT_AUTHORIZED.getExternalValue());
		}
	}

	@Test
	@Disabled
	public void sendGoodbyeMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			GoodbyeMessage goodbyeMessage = wc.sendMessageWithResult(new GoodbyeMessage(WampError.NOT_AUTHORIZED));
			assertThat(goodbyeMessage.getCode()).isEqualTo(6);
			assertThat(goodbyeMessage.getMessage()).isNull();
			assertThat(goodbyeMessage.getReason()).isEqualTo(WampError.GOODBYE_AND_OUT.getExternalValue());

			try {
				wc.sendMessage(new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableServletWamp
	static class Config {

		@Bean
		TicketWampAuthenticationProvider ticketWampAuthenticationProvider() {
			return new TicketWampAuthenticationProvider((authId, ticket, helloAuthExtra, authenticateExtra) -> {
				if ("alice".equals(authId) && "demo-ticket".equals(ticket)) {
					return new WampAuthentication(authId, "admin");
				}
				return null;
			});
		}

	}

}
