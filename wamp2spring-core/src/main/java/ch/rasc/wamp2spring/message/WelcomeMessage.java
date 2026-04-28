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
package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * [WELCOME, Session|id, Details|dict]
 */
public class WelcomeMessage extends WampMessage {

	static final int CODE = 2;

	private final long sessionId;

	private final List<WampRole> roles;

	@Nullable private final String authId;

	@Nullable private final String authRole;

	@Nullable private final String authMethod;

	@Nullable private final String authProvider;

	@Nullable private final Map<String, Object> authExtra;

	public WelcomeMessage(long sessionId, List<WampRole> roles) {
		this(sessionId, roles, null, null, null, null, null);
	}

	public WelcomeMessage(long sessionId, List<WampRole> roles, @Nullable String authId, @Nullable String authRole,
			@Nullable String authMethod, @Nullable String authProvider, @Nullable Map<String, Object> authExtra) {
		super(CODE);
		this.sessionId = sessionId;
		this.roles = roles;
		this.authId = authId;
		this.authRole = authRole;
		this.authMethod = authMethod;
		this.authProvider = authProvider;
		this.authExtra = authExtra != null ? Collections.unmodifiableMap(authExtra) : null;
	}

	public WelcomeMessage(HelloMessage helloMessage, long sessionId, List<WampRole> roles) {
		this(sessionId, roles);
		setReceiver(helloMessage);
		setHeader(WampMessageHeader.WAMP_SESSION_ID, sessionId);
	}

	@SuppressWarnings("unchecked")
	public static WelcomeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long session = jp.getLongValue();

		List<WampRole> roles = new ArrayList<>();
		String authId = null;
		String authRole = null;
		String authMethod = null;
		String authProvider = null;
		Map<String, Object> authExtra = null;
		jp.nextToken();
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			Map<String, Map<String, Map<String, Boolean>>> rolesMap = (Map<String, Map<String, Map<String, Boolean>>>) details
				.get("roles");
			if (rolesMap != null) {
				for (Map.Entry<String, Map<String, Map<String, Boolean>>> entry : rolesMap.entrySet()) {
					WampRole wampRole = new WampRole(entry.getKey());
					Map<String, Boolean> features = entry.getValue().get("features");
					if (features != null) {
						for (String feature : features.keySet()) {
							wampRole.addFeature(feature);
						}
					}
					roles.add(wampRole);
				}
			}

			authId = (String) details.get("authid");
			authRole = (String) details.get("authrole");
			authMethod = (String) details.get("authmethod");
			authProvider = (String) details.get("authprovider");
			authExtra = (Map<String, Object>) details.get("authextra");
		}

		return new WelcomeMessage(session, roles, authId, authRole, authMethod, authProvider, authExtra);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.sessionId);

		generator.writeStartObject();

		generator.writeObjectPropertyStart("roles");
		for (WampRole wampRole : this.roles) {
			generator.writeObjectPropertyStart(wampRole.getRole());
			if (wampRole.hasFeatures()) {
				generator.writeObjectPropertyStart("features");
				for (String feature : wampRole.getFeatures()) {
					generator.writeBooleanProperty(feature, true);
				}
				generator.writeEndObject();
			}
			generator.writeEndObject();
		}
		generator.writeEndObject();

		if (this.authId != null) {
			generator.writeStringProperty("authid", this.authId);
		}
		if (this.authRole != null) {
			generator.writeStringProperty("authrole", this.authRole);
		}
		if (this.authMethod != null) {
			generator.writeStringProperty("authmethod", this.authMethod);
		}
		if (this.authProvider != null) {
			generator.writeStringProperty("authprovider", this.authProvider);
		}
		if (this.authExtra != null && !this.authExtra.isEmpty()) {
			generator.writePOJOProperty("authextra", this.authExtra);
		}
		generator.writeEndObject();
	}

	public long getSessionId() {
		return this.sessionId;
	}

	public List<WampRole> getRoles() {
		return this.roles;
	}

	@Override
	@Nullable public String getAuthId() {
		if (this.authId != null) {
			return this.authId;
		}
		return getPrincipal() != null ? super.getAuthId() : null;
	}

	@Override
	@Nullable public String getAuthRole() {
		if (this.authRole != null) {
			return this.authRole;
		}
		return getPrincipal() != null ? super.getAuthRole() : null;
	}

	@Override
	public String getAuthMethod() {
		if (this.authMethod != null) {
			return this.authMethod;
		}
		return super.getAuthMethod();
	}

	@Override
	public String getAuthProvider() {
		if (this.authProvider != null) {
			return this.authProvider;
		}
		return super.getAuthProvider();
	}

	@Nullable public Map<String, Object> getAuthExtra() {
		return this.authExtra;
	}

	@Override
	public String toString() {
		return "WelcomeMessage [sessionId=" + this.sessionId + ", roles=" + this.roles + ", authId=" + this.authId
				+ ", authRole=" + this.authRole + ", authMethod=" + this.authMethod + "]";
	}

}
