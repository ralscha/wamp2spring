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
package ch.rasc.wamp2spring.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampRole;

public class DynamicAuthenticationProviderTest {

	@Test
	public void delegatesChallengeAndAuthenticate() {
		DynamicAuthenticationProvider provider = new DynamicAuthenticationProvider("signed-token",
				new DynamicAuthenticationProcedure() {
					@Override
					public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
						assertThat(helloMessage.getAuthId()).isEqualTo("alice");
						return new WampAuthenticationChallenge(Map.of("nonce", "abc123"), "server-state");
					}

					@Override
					public WampAuthentication authenticate(HelloMessage helloMessage,
							AuthenticateMessage authenticateMessage, Object challengeState) {
						assertThat(helloMessage.getAuthExtra()).containsEntry("tenant", "demo");
						assertThat(authenticateMessage.getSignature()).isEqualTo("signed-proof");
						assertThat(authenticateMessage.getExtra()).containsEntry("step", "final");
						assertThat(challengeState).isEqualTo("server-state");
						return new WampAuthentication("alice", "admin", "dynamic", Map.of("issued_by", "test-suite"));
					}
				});

		HelloMessage helloMessage = new HelloMessage(List.of(new WampRole("caller")), List.of("signed-token"), "alice",
				Map.of("tenant", "demo"));
		WampAuthenticationChallenge challenge = provider.challenge(helloMessage);

		assertThat(provider.getAuthMethod()).isEqualTo("signed-token");
		assertThat(challenge.getExtra()).containsEntry("nonce", "abc123");
		assertThat(challenge.getState()).isEqualTo("server-state");

		WampAuthentication authentication = provider.authenticate(helloMessage,
				new AuthenticateMessage("signed-proof", Map.of("step", "final")), challenge);

		assertThat(authentication.getAuthId()).isEqualTo("alice");
		assertThat(authentication.getAuthRole()).isEqualTo("admin");
		assertThat(authentication.getAuthProvider()).isEqualTo("dynamic");
		assertThat(authentication.getAuthExtra()).containsEntry("issued_by", "test-suite");
	}

	@Test
	public void throwsNotAuthorizedWhenProcedureDeniesAuthentication() {
		DynamicAuthenticationProvider denyingProvider = new DynamicAuthenticationProvider(
				new DynamicAuthenticationProcedure() {
					@Override
					public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
						return new WampAuthenticationChallenge(Map.of("challenge", "dynamic"), "state");
					}

					@Override
					public WampAuthentication authenticate(HelloMessage helloMessage,
							AuthenticateMessage authenticateMessage, Object challengeState) {
						return null;
					}
				});

		HelloMessage helloMessage = new HelloMessage(List.of(new WampRole("caller")), List.of("dynamic"), "alice",
				null);
		WampAuthenticationChallenge challenge = denyingProvider.challenge(helloMessage);

		assertThatThrownBy(
				() -> denyingProvider.authenticate(helloMessage, new AuthenticateMessage("wrong", null), challenge))
			.isInstanceOf(WampAuthenticationException.class)
			.satisfies(ex -> assertThat(((WampAuthenticationException) ex).getError())
				.isEqualTo(WampError.NOT_AUTHORIZED));
	}

}