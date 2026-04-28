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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;

public class WampScramAuthenticationProvider implements WampAuthenticationProvider {

	private static final String AUTH_METHOD = "wamp-scram";

	private static final String INVALID_AUTH_MESSAGE = "WAMP-SCRAM authentication failed.";

	private final SecureRandom secureRandom = new SecureRandom();

	private final WampScramAuthenticationInfoProvider authenticationInfoProvider;

	public WampScramAuthenticationProvider(WampScramAuthenticationInfoProvider authenticationInfoProvider) {
		this.authenticationInfoProvider = authenticationInfoProvider;
	}

	@Override
	public String getAuthMethod() {
		return AUTH_METHOD;
	}

	@Override
	public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
		String authId = helloMessage.getAuthId();
		if (authId == null || authId.isBlank()) {
			throw new WampAuthenticationException(WampError.INVALID_ARGUMENT,
					"WAMP-SCRAM authentication requires HELLO.Details.authid.");
		}

		String clientNonce = stringExtra(helloMessage.getAuthExtra(), "nonce");
		if (clientNonce == null || clientNonce.isBlank()) {
			throw new WampAuthenticationException(WampError.INVALID_ARGUMENT,
					"WAMP-SCRAM authentication requires HELLO.Details.authextra.nonce.");
		}

		WampScramAuthenticationInfo authenticationInfo = this.authenticationInfoProvider.getAuthenticationInfo(authId,
				helloMessage.getAuthExtra());
		if (authenticationInfo == null) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, INVALID_AUTH_MESSAGE);
		}

		String channelBinding = stringExtra(helloMessage.getAuthExtra(), "channel_binding");
		String serverNonce = clientNonce + randomNonce();
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("nonce", serverNonce);
		extra.put("salt", authenticationInfo.getSalt());
		extra.put("kdf", authenticationInfo.getKdf());
		extra.put("iterations", authenticationInfo.getIterations());
		extra.put("memory", authenticationInfo.getMemory());
		return new WampAuthenticationChallenge(extra,
				new ChallengeState(authenticationInfo, clientNonce, serverNonce, channelBinding));
	}

	@Override
	public WampAuthentication authenticate(HelloMessage helloMessage, AuthenticateMessage authenticateMessage,
			WampAuthenticationChallenge challenge) {
		Object state = challenge.getState();
		if (!(state instanceof ChallengeState challengeState)) {
			throw new WampAuthenticationException(WampError.AUTHORIZATION_FAILED,
					"Missing WAMP-SCRAM challenge state.");
		}

		Map<String, Object> authenticateExtra = authenticateMessage.getExtra();
		String nonce = stringExtra(authenticateExtra, "nonce");
		String channelBinding = stringExtra(authenticateExtra, "channel_binding");
		String cbindData = stringExtra(authenticateExtra, "cbind_data");
		if (!Objects.equals(challengeState.serverNonce(), nonce)
				|| !Objects.equals(challengeState.channelBinding(), channelBinding)) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, INVALID_AUTH_MESSAGE);
		}

		String authId = Objects.requireNonNull(helloMessage.getAuthId());
		String authMessage = WampScram.authMessage(authId, challengeState.clientNonce(), challengeState.serverNonce(),
				challengeState.authenticationInfo().getSalt(), challengeState.authenticationInfo().getIterations(),
				channelBinding, cbindData);
		final String verifier;
		try {
			verifier = WampScram.verifyClientProof(authenticateMessage.getSignature(),
					challengeState.authenticationInfo().getStoredKey(),
					challengeState.authenticationInfo().getServerKey(), authMessage);
		}
		catch (IllegalArgumentException e) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, INVALID_AUTH_MESSAGE);
		}

		WampAuthentication authentication = challengeState.authenticationInfo().getAuthentication();
		Map<String, Object> authExtra = new LinkedHashMap<>();
		if (authentication.getAuthExtra() != null) {
			authExtra.putAll(authentication.getAuthExtra());
		}
		authExtra.put("verifier", verifier);
		return new WampAuthentication(authentication.getAuthId(), authentication.getAuthRole(),
				authentication.getAuthProvider(), authExtra);
	}

	private String randomNonce() {
		byte[] nonce = new byte[16];
		this.secureRandom.nextBytes(nonce);
		return Base64.getEncoder().encodeToString(nonce);
	}

	private static @Nullable String stringExtra(@Nullable Map<String, Object> extra, String key) {
		if (extra == null) {
			return null;
		}
		Object value = extra.get(key);
		return value instanceof String stringValue ? stringValue : null;
	}

	private record ChallengeState(WampScramAuthenticationInfo authenticationInfo, String clientNonce,
			String serverNonce, @Nullable String channelBinding) {
	}

}