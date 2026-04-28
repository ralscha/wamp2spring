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

import java.util.Objects;

public final class WampCraAuthenticationInfo {

	private final String secret;

	private final WampAuthentication authentication;

	public WampCraAuthenticationInfo(String secret, WampAuthentication authentication) {
		this.secret = Objects.requireNonNull(secret);
		this.authentication = Objects.requireNonNull(authentication);
	}

	public String getSecret() {
		return this.secret;
	}

	public WampAuthentication getAuthentication() {
		return this.authentication;
	}

}