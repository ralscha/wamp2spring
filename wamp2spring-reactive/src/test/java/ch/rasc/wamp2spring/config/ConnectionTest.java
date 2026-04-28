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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketSession;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.auth.DynamicAuthenticationProcedure;
import ch.rasc.wamp2spring.auth.DynamicAuthenticationProvider;
import ch.rasc.wamp2spring.auth.TicketWampAuthenticationProvider;
import ch.rasc.wamp2spring.auth.WampAuthentication;
import ch.rasc.wamp2spring.auth.WampCraAuthenticationInfo;
import ch.rasc.wamp2spring.auth.WampCraAuthenticationProvider;
import ch.rasc.wamp2spring.auth.WampAuthenticationChallenge;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.ChallengeMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.CompletableFutureWebSocketHandler;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConnectionTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
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
				Assertions.fail("sendMessage should fail because the connection should be closed");
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
				Assertions.fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	public void invalidRealmHelloIsRejected() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			sendMessage(DataFormat.JSON, wsSession,
					new HelloMessage("realm with space", List.of(new WampRole("publisher"))));

			AbortMessage abortMessage = (AbortMessage) result.getWampMessage();
			assertThat(abortMessage.getReason()).isEqualTo(WampError.INVALID_URI.getExternalValue());
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
	public void wampCraAuthenticationSuccessTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			HelloMessage helloMessage = new HelloMessage("realm",
					List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")),
					List.of("wampcra"), "alice", Map.of("tenant", "demo"));
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("wampcra");
			assertThat(challengeMessage.getExtra()).containsKey("challenge");

			result.reset();
			String challenge = Objects.requireNonNull((String) challengeMessage.getExtra().get("challenge"));
			sendMessage(DataFormat.JSON, wsSession,
					new AuthenticateMessage(wampCraSignature("demo-secret", challenge), null));

			WelcomeMessage welcomeMessage = result.getWelcomeMessage();
			assertThat(welcomeMessage.getAuthId()).isEqualTo("alice");
			assertThat(welcomeMessage.getAuthRole()).isEqualTo("admin");
			assertThat(welcomeMessage.getAuthMethod()).isEqualTo("wampcra");
			assertThat(welcomeMessage.getAuthProvider()).isEqualTo("static");
		}
	}

	@Test
	public void wampCraAuthenticationFailureTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			HelloMessage helloMessage = new HelloMessage("realm",
					List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")),
					List.of("wampcra"), "alice", null);
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("wampcra");

			result.reset();
			String challenge = Objects.requireNonNull((String) challengeMessage.getExtra().get("challenge"));
			sendMessage(DataFormat.JSON, wsSession,
					new AuthenticateMessage(wampCraSignature("wrong-secret", challenge), null));

			AbortMessage abortMessage = (AbortMessage) result.getWampMessage();
			assertThat(abortMessage.getReason()).isEqualTo(WampError.NOT_AUTHORIZED.getExternalValue());
		}
	}

	@Test
	public void dynamicAuthenticationSuccessTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			HelloMessage helloMessage = new HelloMessage("realm",
					List.of(new WampRole("publisher"), new WampRole("caller")), List.of("dynamic"), "alice",
					Map.of("tenant", "demo"));
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("dynamic");
			assertThat(challengeMessage.getExtra()).containsEntry("challenge", "demo:alice");
			assertThat(challengeMessage.getExtra()).containsEntry("tenant", "demo");

			result.reset();
			sendMessage(DataFormat.JSON, wsSession,
					new AuthenticateMessage("proof:nonce-123", Map.of("channel_binding", "none")));

			WelcomeMessage welcomeMessage = result.getWelcomeMessage();
			assertThat(welcomeMessage.getAuthId()).isEqualTo("alice");
			assertThat(welcomeMessage.getAuthRole()).isEqualTo("admin");
			assertThat(welcomeMessage.getAuthMethod()).isEqualTo("dynamic");
			assertThat(welcomeMessage.getAuthProvider()).isEqualTo("dynamic-procedure");
			assertThat(welcomeMessage.getAuthExtra()).containsEntry("tenant", "demo");
			assertThat(welcomeMessage.getAuthExtra()).containsEntry("challenge_type", "nonce");
		}
	}

	@Test
	public void dynamicAuthenticationFailureTest() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		try (WebSocketSession wsSession = startWebSocketSession(result, DataFormat.JSON)) {
			HelloMessage helloMessage = new HelloMessage("realm", List.of(new WampRole("caller")), List.of("dynamic"),
					"alice", Map.of("tenant", "demo"));
			sendMessage(DataFormat.JSON, wsSession, helloMessage);

			ChallengeMessage challengeMessage = (ChallengeMessage) result.getWampMessage();
			assertThat(challengeMessage.getAuthMethod()).isEqualTo("dynamic");

			result.reset();
			sendMessage(DataFormat.JSON, wsSession,
					new AuthenticateMessage("proof:wrong", Map.of("channel_binding", "none")));

			AbortMessage abortMessage = (AbortMessage) result.getWampMessage();
			assertThat(abortMessage.getReason()).isEqualTo(WampError.NOT_AUTHORIZED.getExternalValue());
		}
	}

	@Test
	public void sendGoodbyeMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			GoodbyeMessage goodbyeMessage = wc.sendMessageWithResult(new GoodbyeMessage(WampError.NOT_AUTHORIZED));
			assertThat(goodbyeMessage.getCode()).isEqualTo(6);
			assertThat(goodbyeMessage.getMessage()).isNull();
			assertThat(goodbyeMessage.getReason()).isEqualTo(WampError.GOODBYE_AND_OUT.getExternalValue());

			try {
				TimeUnit.SECONDS.sleep(5);
				wc.sendMessage(new PublishMessage.Builder(1L, "crud.user.create").build());
				Assertions.fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
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

		@Bean
		WampCraAuthenticationProvider wampCraAuthenticationProvider() {
			return new WampCraAuthenticationProvider((authId, helloAuthExtra) -> {
				if ("alice".equals(authId)) {
					return new WampCraAuthenticationInfo("demo-secret", new WampAuthentication(authId, "admin"));
				}
				return null;
			});
		}

		@Bean
		DynamicAuthenticationProvider dynamicAuthenticationProvider() {
			return new DynamicAuthenticationProvider(new DynamicAuthenticationProcedure() {
				@Override
				public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
					String authId = Objects.requireNonNull(helloMessage.getAuthId());
					String tenant = Objects
						.requireNonNull((String) Objects.requireNonNull(helloMessage.getAuthExtra()).get("tenant"));
					return new WampAuthenticationChallenge(Map.of("challenge", tenant + ":" + authId, "tenant", tenant),
							"nonce-123");
				}

				@Override
				public @Nullable WampAuthentication authenticate(HelloMessage helloMessage,
						AuthenticateMessage authenticateMessage, @Nullable Object challengeState) {
					if ("alice".equals(helloMessage.getAuthId())
							&& ("proof:" + challengeState).equals(authenticateMessage.getSignature())) {
						return new WampAuthentication(helloMessage.getAuthId(), "admin", "dynamic-procedure",
								Map.of("tenant", Objects.requireNonNull(helloMessage.getAuthExtra()).get("tenant"),
										"challenge_type", "nonce"));
					}
					return null;
				}
			});
		}

	}

	private static String wampCraSignature(String secret, String challenge) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signature = mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signature);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
