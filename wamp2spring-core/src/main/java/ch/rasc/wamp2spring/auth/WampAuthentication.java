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

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public final class WampAuthentication {

	private final Principal principal;

	@Nullable private final String authRole;

	private final String authProvider;

	@Nullable private final Map<String, Object> authExtra;

	public WampAuthentication(String authId, @Nullable String authRole) {
		this(authId, authRole, "static", null);
	}

	public WampAuthentication(String authId, @Nullable String authRole, String authProvider,
			@Nullable Map<String, Object> authExtra) {
		this.principal = new WampAuthenticationPrincipal(authId, authRole);
		this.authRole = authRole;
		this.authProvider = authProvider;
		this.authExtra = authExtra != null ? Collections.unmodifiableMap(authExtra) : null;
	}

	public Principal getPrincipal() {
		return this.principal;
	}

	public String getAuthId() {
		return this.principal.getName();
	}

	@Nullable public String getAuthRole() {
		return this.authRole;
	}

	public String getAuthProvider() {
		return this.authProvider;
	}

	@Nullable public Map<String, Object> getAuthExtra() {
		return this.authExtra;
	}

	private static final class WampAuthenticationPrincipal implements Principal {

		private final String name;

		private final List<WampAuthority> authorities;

		private WampAuthenticationPrincipal(String name, @Nullable String authRole) {
			this.name = name;
			this.authorities = authRole != null ? List.of(new WampAuthority("ROLE_" + authRole)) : List.of();
		}

		@Override
		public String getName() {
			return this.name;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public List<WampAuthority> getAuthorities() {
			return this.authorities;
		}

	}

	private static final class WampAuthority {

		private final String authority;

		private WampAuthority(String authority) {
			this.authority = authority;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public String getAuthority() {
			return this.authority;
		}

	}

}