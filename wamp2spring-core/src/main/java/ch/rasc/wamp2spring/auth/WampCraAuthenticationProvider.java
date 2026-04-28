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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;

public class WampCraAuthenticationProvider implements WampAuthenticationProvider {

	private static final String AUTH_METHOD = "wampcra";

	private final SecureRandom secureRandom = new SecureRandom();

	private final WampCraAuthenticationInfoProvider authenticationInfoProvider;

	public WampCraAuthenticationProvider(WampCraAuthenticationInfoProvider authenticationInfoProvider) {
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
					"WAMP-CRA authentication requires HELLO.Details.authid.");
		}

		WampCraAuthenticationInfo authenticationInfo = this.authenticationInfoProvider.getAuthenticationInfo(authId,
				helloMessage.getAuthExtra());
		if (authenticationInfo == null) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, "WAMP-CRA authentication failed.");
		}

		String challenge = createChallenge(authenticationInfo.getAuthentication());
		return new WampAuthenticationChallenge(Map.of("challenge", challenge),
				new ChallengeState(authenticationInfo, challenge));
	}

	@Override
	public WampAuthentication authenticate(HelloMessage helloMessage, AuthenticateMessage authenticateMessage,
			WampAuthenticationChallenge challenge) {
		Object state = challenge.getState();
		if (!(state instanceof ChallengeState challengeState)) {
			throw new WampAuthenticationException(WampError.AUTHORIZATION_FAILED, "Missing WAMP-CRA challenge state.");
		}

		String expectedSignature = sign(challengeState.authenticationInfo().getSecret(), challengeState.challenge());
		byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
		byte[] actualBytes = authenticateMessage.getSignature().getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, "WAMP-CRA authentication failed.");
		}

		return challengeState.authenticationInfo().getAuthentication();
	}

	private String createChallenge(WampAuthentication authentication) {
		StringBuilder builder = new StringBuilder(160);
		builder.append('{');
		appendJsonField(builder, "nonce", randomNonce());
		appendJsonField(builder, "authprovider", authentication.getAuthProvider());
		appendJsonField(builder, "authid", authentication.getAuthId());
		if (authentication.getAuthRole() != null) {
			appendJsonField(builder, "authrole", authentication.getAuthRole());
		}
		appendJsonField(builder, "authmethod", AUTH_METHOD);
		appendJsonField(builder, "timestamp", Instant.now().toString());
		builder.append('}');
		return builder.toString();
	}

	private String randomNonce() {
		byte[] nonce = new byte[18];
		this.secureRandom.nextBytes(nonce);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
	}

	private static void appendJsonField(StringBuilder builder, String key, String value) {
		if (builder.length() > 1) {
			builder.append(',');
		}
		builder.append('"')
			.append(escapeJson(key))
			.append('"')
			.append(':')
			.append('"')
			.append(escapeJson(value))
			.append('"');
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String sign(String secret, String challenge) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signature = mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signature);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to sign WAMP-CRA challenge.", e);
		}
	}

	private record ChallengeState(WampCraAuthenticationInfo authenticationInfo, String challenge) {
	}

}