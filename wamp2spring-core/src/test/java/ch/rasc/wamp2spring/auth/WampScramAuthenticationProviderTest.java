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

public class WampScramAuthenticationProviderTest {

	@Test
	public void authenticatesUsingPbkdf2ProofAndReturnsServerVerifier() {
		WampScramAuthenticationProvider provider = new WampScramAuthenticationProvider((authId, helloAuthExtra) -> {
			assertThat(authId).isEqualTo("alice");
			assertThat(helloAuthExtra).containsEntry("tenant", "demo");
			return WampScramAuthenticationInfo.pbkdf2("correct horse battery staple", "QSXCR+Q6sek8bf92", 4096,
					new WampAuthentication("alice", "admin", "static", Map.of("tenant", "demo")));
		});

		HelloMessage helloMessage = new HelloMessage("realm", List.of(new WampRole("caller")), List.of("wamp-scram"),
				"alice", Map.of("nonce", "fyko+d2lbbFgONRv9qkxdawL", "tenant", "demo"));

		WampAuthenticationChallenge challenge = provider.challenge(helloMessage);

		assertThat(provider.getAuthMethod()).isEqualTo("wamp-scram");
		assertThat(challenge.getExtra()).containsEntry("salt", "QSXCR+Q6sek8bf92");
		assertThat(challenge.getExtra()).containsEntry("kdf", "pbkdf2");
		assertThat(challenge.getExtra()).containsEntry("iterations", 4096);
		assertThat(challenge.getExtra()).containsEntry("memory", null);

		String serverNonce = (String) challenge.getExtra().get("nonce");
		WampScram.ClientProof proof = WampScram.createClientProof("correct horse battery staple", "alice",
				"fyko+d2lbbFgONRv9qkxdawL", serverNonce, "QSXCR+Q6sek8bf92", 4096, null, "pbkdf2", null, null);

		WampAuthentication authentication = provider.authenticate(helloMessage,
				new AuthenticateMessage(proof.proof(), Map.of("nonce", serverNonce)), challenge);

		assertThat(authentication.getAuthId()).isEqualTo("alice");
		assertThat(authentication.getAuthRole()).isEqualTo("admin");
		assertThat(authentication.getAuthProvider()).isEqualTo("static");
		assertThat(authentication.getAuthExtra()).containsEntry("tenant", "demo");
		assertThat(authentication.getAuthExtra()).containsKey("verifier");
	}

	@Test
	public void rejectsInvalidProof() {
		WampScramAuthenticationProvider provider = new WampScramAuthenticationProvider(
				(authId, helloAuthExtra) -> WampScramAuthenticationInfo.pbkdf2("secret", "QSXCR+Q6sek8bf92", 4096,
						new WampAuthentication(authId, "admin")));

		HelloMessage helloMessage = new HelloMessage("realm", List.of(new WampRole("caller")), List.of("wamp-scram"),
				"alice", Map.of("nonce", "clientNonce=="));
		WampAuthenticationChallenge challenge = provider.challenge(helloMessage);

		assertThatThrownBy(() -> provider.authenticate(helloMessage,
				new AuthenticateMessage("invalid-proof", Map.of("nonce", challenge.getExtra().get("nonce"))),
				challenge))
			.isInstanceOf(WampAuthenticationException.class)
			.satisfies(ex -> assertThat(((WampAuthenticationException) ex).getError())
				.isEqualTo(WampError.NOT_AUTHORIZED));
	}

	@Test
	public void requiresClientNonceInHelloAuthextra() {
		WampScramAuthenticationProvider provider = new WampScramAuthenticationProvider(
				(authId, helloAuthExtra) -> WampScramAuthenticationInfo.pbkdf2("secret", "QSXCR+Q6sek8bf92", 4096,
						new WampAuthentication(authId, "admin")));

		HelloMessage helloMessage = new HelloMessage("realm", List.of(new WampRole("caller")), List.of("wamp-scram"),
				"alice", null);

		assertThatThrownBy(() -> provider.challenge(helloMessage)).isInstanceOf(WampAuthenticationException.class)
			.satisfies(ex -> assertThat(((WampAuthenticationException) ex).getError())
				.isEqualTo(WampError.INVALID_ARGUMENT));
	}

}