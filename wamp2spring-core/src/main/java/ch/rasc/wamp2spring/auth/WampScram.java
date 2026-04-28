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
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jspecify.annotations.Nullable;

final class WampScram {

	private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

	private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	private WampScram() {
	}

	static StoredCredentials createStoredCredentials(String password, String kdf, String salt, int iterations,
			@Nullable Integer memory) {
		byte[] saltedPassword = derivePassword(password, kdf, salt, iterations, memory);
		byte[] clientKey = hmac(saltedPassword, "Client Key");
		byte[] storedKey = sha256(clientKey);
		byte[] serverKey = hmac(saltedPassword, "Server Key");
		return new StoredCredentials(BASE64_ENCODER.encodeToString(storedKey),
				BASE64_ENCODER.encodeToString(serverKey));
	}

	static ClientProof createClientProof(String password, String authId, String clientNonce, String serverNonce,
			String salt, int iterations, @Nullable Integer memory, String kdf, @Nullable String channelBinding,
			@Nullable String cbindData) {
		String authMessage = authMessage(authId, clientNonce, serverNonce, salt, iterations, channelBinding, cbindData);
		byte[] saltedPassword = derivePassword(password, kdf, salt, iterations, memory);
		byte[] clientKey = hmac(saltedPassword, "Client Key");
		byte[] storedKey = sha256(clientKey);
		byte[] clientSignature = hmac(storedKey, authMessage);
		byte[] clientProof = xor(clientKey, clientSignature);
		return new ClientProof(BASE64_ENCODER.encodeToString(clientProof), authMessage);
	}

	static String authMessage(String authId, String clientNonce, String serverNonce, String salt, int iterations,
			@Nullable String channelBinding, @Nullable String cbindData) {
		String clientFirstBare = "n=" + escape(authId) + ",r=" + clientNonce;
		String serverFirst = "r=" + serverNonce + ",s=" + salt + ",i=" + iterations;
		String cbindFlag = channelBinding == null ? "n" : "p=" + channelBinding;
		String cbindInput = cbindFlag + ",,"
				+ (cbindData == null ? "" : new String(BASE64_DECODER.decode(cbindData), StandardCharsets.UTF_8));
		String clientFinalNoProof = "c=" + BASE64_ENCODER.encodeToString(cbindInput.getBytes(StandardCharsets.UTF_8))
				+ ",r=" + serverNonce;
		return clientFirstBare + ',' + serverFirst + ',' + clientFinalNoProof;
	}

	static String verifyClientProof(String proof, String storedKey, String serverKey, String authMessage) {
		byte[] storedKeyBytes = BASE64_DECODER.decode(storedKey);
		byte[] clientSignature = hmac(storedKeyBytes, authMessage);
		byte[] receivedProof = BASE64_DECODER.decode(proof);
		byte[] recoveredClientKey = xor(clientSignature, receivedProof);
		byte[] recoveredStoredKey = sha256(recoveredClientKey);
		if (!MessageDigest.isEqual(storedKeyBytes, recoveredStoredKey)) {
			throw new IllegalArgumentException("Client proof verification failed.");
		}
		byte[] serverKeyBytes = BASE64_DECODER.decode(serverKey);
		return BASE64_ENCODER.encodeToString(hmac(serverKeyBytes, authMessage));
	}

	private static byte[] derivePassword(String password, String kdf, String salt, int iterations,
			@Nullable Integer memory) {
		return switch (kdf) {
			case "pbkdf2" -> pbkdf2(password, salt, iterations);
			case "argon2id13" -> argon2id13(password, salt, iterations, memory);
			default -> throw new IllegalArgumentException("Unsupported WAMP-SCRAM kdf: " + kdf);
		};
	}

	private static byte[] pbkdf2(String password, String salt, int iterations) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), BASE64_DECODER.decode(salt), iterations, 256);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return factory.generateSecret(spec).getEncoded();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to derive PBKDF2 credentials for WAMP-SCRAM.", e);
		}
	}

	private static byte[] argon2id13(String password, String salt, int iterations, @Nullable Integer memory) {
		if (memory == null) {
			throw new IllegalArgumentException("Argon2 WAMP-SCRAM credentials require a memory cost.");
		}
		Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
			.withVersion(Argon2Parameters.ARGON2_VERSION_13)
			.withIterations(iterations)
			.withMemoryAsKB(memory)
			.withParallelism(1)
			.withSalt(BASE64_DECODER.decode(salt))
			.build();
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(parameters);
		byte[] output = new byte[32];
		generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), output);
		return output;
	}

	private static byte[] hmac(byte[] key, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to compute WAMP-SCRAM HMAC.", e);
		}
	}

	private static byte[] sha256(byte[] value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to compute WAMP-SCRAM hash.", e);
		}
	}

	private static byte[] xor(byte[] left, byte[] right) {
		if (left.length != right.length) {
			throw new IllegalArgumentException("WAMP-SCRAM byte arrays must have equal length.");
		}
		byte[] result = new byte[left.length];
		for (int i = 0; i < left.length; i++) {
			result[i] = (byte) (left[i] ^ right[i]);
		}
		return result;
	}

	private static String escape(String value) {
		return value.replace("=", "=3D").replace(",", "=2C");
	}

	record StoredCredentials(String storedKey, String serverKey) {
	}

	record ClientProof(String proof, String authMessage) {
	}

}