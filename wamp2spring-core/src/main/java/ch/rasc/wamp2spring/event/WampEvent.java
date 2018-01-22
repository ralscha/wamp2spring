/**
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.wamp2spring.event;

import java.security.Principal;

import org.springframework.lang.Nullable;

/**
 * Base class for the WAMP events
 */
public abstract class WampEvent {
	@Nullable
	private final Principal principal;

	private final Long wampSessionId;

	private final String webSocketSessionId;

	public WampEvent(Long wampSessionId, String webSocketSessionId,
			@Nullable Principal principal) {
		this.wampSessionId = wampSessionId;
		this.principal = principal;
		this.webSocketSessionId = webSocketSessionId;
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
	@Nullable
	public Principal getPrincipal() {
		return this.principal;
	}

	/**
	 * Returns the unique WAMP session identifier. There is a one-to-one relation with the
	 * {@link #getWebSocketSessionId()}.
	 */
	public Long getWampSessionId() {
		return this.wampSessionId;
	}

}
