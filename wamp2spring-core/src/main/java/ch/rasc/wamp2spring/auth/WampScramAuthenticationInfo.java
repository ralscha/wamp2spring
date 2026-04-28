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

import org.jspecify.annotations.Nullable;

public final class WampScramAuthenticationInfo {

	private final String kdf;

	private final String salt;

	private final int iterations;

	@Nullable private final Integer memory;

	private final String storedKey;

	private final String serverKey;

	private final WampAuthentication authentication;

	public WampScramAuthenticationInfo(String kdf, String salt, int iterations, @Nullable Integer memory,
			String storedKey, String serverKey, WampAuthentication authentication) {
		this.kdf = kdf;
		this.salt = salt;
		this.iterations = iterations;
		this.memory = memory;
		this.storedKey = storedKey;
		this.serverKey = serverKey;
		this.authentication = authentication;
	}

	public String getKdf() {
		return this.kdf;
	}

	public String getSalt() {
		return this.salt;
	}

	public int getIterations() {
		return this.iterations;
	}

	public @Nullable Integer getMemory() {
		return this.memory;
	}

	public String getStoredKey() {
		return this.storedKey;
	}

	public String getServerKey() {
		return this.serverKey;
	}

	public WampAuthentication getAuthentication() {
		return this.authentication;
	}

	public static WampScramAuthenticationInfo pbkdf2(String password, String salt, int iterations,
			WampAuthentication authentication) {
		WampScram.StoredCredentials credentials = WampScram.createStoredCredentials(password, "pbkdf2", salt,
				iterations, null);
		return new WampScramAuthenticationInfo("pbkdf2", salt, iterations, null, credentials.storedKey(),
				credentials.serverKey(), authentication);
	}

	public static WampScramAuthenticationInfo argon2id13(String password, String salt, int iterations, int memory,
			WampAuthentication authentication) {
		WampScram.StoredCredentials credentials = WampScram.createStoredCredentials(password, "argon2id13", salt,
				iterations, memory);
		return new WampScramAuthenticationInfo("argon2id13", salt, iterations, memory, credentials.storedKey(),
				credentials.serverKey(), authentication);
	}

}