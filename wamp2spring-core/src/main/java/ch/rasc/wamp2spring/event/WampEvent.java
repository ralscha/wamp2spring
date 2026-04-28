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
package ch.rasc.wamp2spring.event;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Principal;

import org.jspecify.annotations.Nullable;

/**
 * Base class for the WAMP events
 */
public abstract class WampEvent {

	@Nullable private final Principal principal;

	private final String authMethod;

	private final String authProvider;

	private final Long wampSessionId;

	private final String webSocketSessionId;

	public WampEvent(Long wampSessionId, String webSocketSessionId, @Nullable Principal principal) {
		this(wampSessionId, webSocketSessionId, principal, principal == null ? "anonymous" : "transport",
				principal == null ? "static" : "transport");
	}

	public WampEvent(Long wampSessionId, String webSocketSessionId, @Nullable Principal principal, String authMethod,
			String authProvider) {
		this.wampSessionId = wampSessionId;
		this.principal = principal;
		this.webSocketSessionId = webSocketSessionId;
		this.authMethod = authMethod;
		this.authProvider = authProvider;
	}

	/**
	 * Returns an unique session identifier. Created by the Spring WebSocket layer.
	 */
	public String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	/**
	 * Return a {@link java.security.Principal} instance containing the name of the
	 * authenticated user.
	 * <p>
	 * If the user has not been authenticated, the method returns <code>null</code>.
	 */
	@Nullable public Principal getPrincipal() {
		return this.principal;
	}

	/**
	 * Returns the unique WAMP session identifier. There is a one-to-one relation with the
	 * {@link #getWebSocketSessionId()}.
	 */
	public Long getWampSessionId() {
		return this.wampSessionId;
	}

	@Nullable public String getAuthId() {
		if (this.principal == null) {
			return null;
		}

		String name = this.principal.getName();
		if (name == null || name.isBlank()) {
			return null;
		}
		return name;
	}

	@Nullable public String getAuthRole() {
		if (this.principal == null) {
			return null;
		}

		Object authorities = invokeNoArgMethod(this.principal, "getAuthorities");
		String fallbackAuthority = null;
		for (Object authority : asIterable(authorities)) {
			String authorityValue = extractAuthority(authority);
			if (authorityValue == null || authorityValue.isBlank()) {
				continue;
			}
			if (authorityValue.startsWith("ROLE_")) {
				return authorityValue.substring(5);
			}
			if (fallbackAuthority == null) {
				fallbackAuthority = authorityValue;
			}
		}

		return fallbackAuthority;
	}

	public String getAuthMethod() {
		return this.authMethod;
	}

	public String getAuthProvider() {
		return this.authProvider;
	}

	@Nullable private static Object invokeNoArgMethod(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		}
		catch (Exception ex) {
			try {
				Method declaredMethod = target.getClass().getDeclaredMethod(methodName);
				declaredMethod.setAccessible(true);
				return declaredMethod.invoke(target);
			}
			catch (Exception ignored) {
				return null;
			}
		}
	}

	private static Iterable<?> asIterable(@Nullable Object value) {
		if (value instanceof Iterable<?>) {
			return (Iterable<?>) value;
		}
		if (value != null && value.getClass().isArray()) {
			java.util.ArrayList<Object> values = new java.util.ArrayList<>(Array.getLength(value));
			for (int i = 0; i < Array.getLength(value); i++) {
				values.add(Array.get(value, i));
			}
			return values;
		}
		return java.util.List.of();
	}

	@Nullable private static String extractAuthority(Object authority) {
		if (authority == null) {
			return null;
		}

		Object extractedValue = invokeNoArgMethod(authority, "getAuthority");
		if (extractedValue == null) {
			return authority.toString();
		}

		return extractedValue.toString();
	}

}
