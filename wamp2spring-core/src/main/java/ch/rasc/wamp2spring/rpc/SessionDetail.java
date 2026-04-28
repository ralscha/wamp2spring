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
package ch.rasc.wamp2spring.rpc;

import org.jspecify.annotations.Nullable;

public final class SessionDetail {

	private final long sessionId;

	private final String webSocketSessionId;

	@Nullable private final String authId;

	@Nullable private final String authRole;

	private final String authMethod;

	private final String authProvider;

	private final long created;

	public SessionDetail(long sessionId, String webSocketSessionId, @Nullable String authId, @Nullable String authRole,
			long created) {
		this(sessionId, webSocketSessionId, authId, authRole, authId == null ? "anonymous" : "transport",
				authId == null ? "static" : "transport", created);
	}

	public SessionDetail(long sessionId, String webSocketSessionId, @Nullable String authId, @Nullable String authRole,
			String authMethod, String authProvider, long created) {
		this.sessionId = sessionId;
		this.webSocketSessionId = webSocketSessionId;
		this.authId = authId;
		this.authRole = authRole;
		this.authMethod = authMethod;
		this.authProvider = authProvider;
		this.created = created;
	}

	public long getSessionId() {
		return this.sessionId;
	}

	public String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	@Nullable public String getAuthId() {
		return this.authId;
	}

	@Nullable public String getAuthRole() {
		return this.authRole;
	}

	public String getAuthMethod() {
		return this.authMethod;
	}

	public String getAuthProvider() {
		return this.authProvider;
	}

	public long getCreated() {
		return this.created;
	}

}