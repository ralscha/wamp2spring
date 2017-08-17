/**
 * Copyright 2017-2017 Ralph Schaer <ralphschaer@gmail.com>
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
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [WELCOME, Session|id, Details|dict]
 */
public class WelcomeMessage extends WampMessage {

	static final int CODE = 2;

	private final long sessionId;

	private final List<WampRole> roles;

	@Nullable
	private final String realm;

	public WelcomeMessage(long sessionId, List<WampRole> roles, @Nullable String realm) {
		super(CODE);
		this.sessionId = sessionId;
		this.roles = roles;
		this.realm = realm;
	}

	public WelcomeMessage(HelloMessage helloMessage, long sessionId,
			List<WampRole> roles) {
		this(sessionId, roles, null);
		setReceiver(helloMessage);
		setHeader(WampMessageHeader.WAMP_SESSION_ID, sessionId);
	}

	@SuppressWarnings("unchecked")
	public static WelcomeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long session = jp.getLongValue();

		List<WampRole> roles = new ArrayList<>();
		String realm = null;
		jp.nextToken();
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			Map<String, Map<String, Map<String, Boolean>>> rolesMap = (Map<String, Map<String, Map<String, Boolean>>>) details
					.get("roles");
			for (String roleName : rolesMap.keySet()) {
				WampRole wampRole = new WampRole(roleName);
				Map<String, Boolean> features = rolesMap.get(roleName).get("features");
				if (features != null) {
					for (String feature : features.keySet()) {
						wampRole.addFeature(feature);
					}
				}
				roles.add(wampRole);
			}

			realm = (String) details.get("realm");
		}

		return new WelcomeMessage(session, roles, realm);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.sessionId);

		generator.writeStartObject();

		generator.writeObjectFieldStart("roles");
		for (WampRole wampRole : this.roles) {
			generator.writeObjectFieldStart(wampRole.getRole());
			if (wampRole.hasFeatures()) {
				generator.writeObjectFieldStart("features");
				for (String feature : wampRole.getFeatures()) {
					generator.writeBooleanField(feature, true);
				}
				generator.writeEndObject();
			}
			generator.writeEndObject();
		}
		generator.writeEndObject();

		if (this.realm != null) {
			generator.writeStringField("realm", this.realm);
		}
		generator.writeEndObject();
	}

	public long getSessionId() {
		return this.sessionId;
	}

	public List<WampRole> getRoles() {
		return this.roles;
	}

	@Nullable
	public String getRealm() {
		return this.realm;
	}

	@Override
	public String toString() {
		return "WelcomeMessage [sessionId=" + this.sessionId + ", roles=" + this.roles
				+ ", realm=" + this.realm + "]";
	}

}
