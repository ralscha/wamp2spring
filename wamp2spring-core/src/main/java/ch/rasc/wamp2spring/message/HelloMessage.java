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
 * [HELLO, Details|dict]
 */
public class HelloMessage extends WampMessage {

	static final int CODE = 1;

	private final List<WampRole> roles;

	private final List<String> authMethods;

	@Nullable private final String authId;

	@Nullable private final Map<String, Object> authExtra;

	public HelloMessage(List<WampRole> roles) {
		this(roles, List.of(), null, null);
	}

	public HelloMessage(List<WampRole> roles, @Nullable List<String> authMethods, @Nullable String authId,
			@Nullable Map<String, Object> authExtra) {
		super(CODE);
		this.roles = roles;
		this.authMethods = authMethods != null ? List.copyOf(authMethods) : List.of();
		this.authId = authId;
		this.authExtra = authExtra != null ? Collections.unmodifiableMap(authExtra) : null;
	}

	@SuppressWarnings("unchecked")
	public static HelloMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();

		List<WampRole> roles = new ArrayList<>();
		List<String> authMethods = List.of();
		String authId = null;
		Map<String, Object> authExtra = null;
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

			Object authMethodsValue = details.get("authmethods");
			if (authMethodsValue instanceof List<?>) {
				authMethods = new ArrayList<>();
				for (Object value : (List<?>) authMethodsValue) {
					if (value != null) {
						authMethods.add(value.toString());
					}
				}
			}

			authId = (String) details.get("authid");
			authExtra = (Map<String, Object>) details.get("authextra");
		}

		return new HelloMessage(roles, authMethods, authId, authExtra);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
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
		if (!this.authMethods.isEmpty()) {
			generator.writeArrayPropertyStart("authmethods");
			for (String authMethod : this.authMethods) {
				generator.writeString(authMethod);
			}
			generator.writeEndArray();
		}
		if (this.authId != null) {
			generator.writeStringProperty("authid", this.authId);
		}
		if (this.authExtra != null && !this.authExtra.isEmpty()) {
			generator.writePOJOProperty("authextra", this.authExtra);
		}
		generator.writeEndObject();
	}

	public List<WampRole> getRoles() {
		return this.roles;
	}

	public List<String> getAuthMethods() {
		return this.authMethods;
	}

	@Override
	@Nullable public String getAuthId() {
		return this.authId;
	}

	@Nullable public Map<String, Object> getAuthExtra() {
		return this.authExtra;
	}

	@Override
	public String toString() {
		return "HelloMessage [roles=" + this.roles + ", authMethods=" + this.authMethods + ", authId=" + this.authId
				+ "]";
	}

}
