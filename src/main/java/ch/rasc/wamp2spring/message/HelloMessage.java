/**
 * Copyright 2017-2017 the original author or authors.
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

package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [HELLO, Realm|uri, Details|dict]
 */
public class HelloMessage extends WampMessage {

	static final int CODE = 1;

	@Nullable
	private final String realm;

	private final List<WampRole> roles;

	public HelloMessage(@Nullable String realm, List<WampRole> roles) {
		super(CODE);
		this.realm = realm;
		this.roles = roles;
	}

	@SuppressWarnings("unchecked")
	public static HelloMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		String realm = jp.getValueAsString();

		List<WampRole> roles = new ArrayList<>();
		jp.nextToken();
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			Map<String, Map<String, Map<String, Boolean>>> rolesMap = (Map<String, Map<String, Map<String, Boolean>>>) details
					.get("roles");
			for (Map.Entry<String, Map<String, Map<String, Boolean>>> entry : rolesMap
					.entrySet()) {
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

		return new HelloMessage(realm, roles);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeString(this.realm);
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
		generator.writeEndObject();
	}

	@Nullable
	public String getRealm() {
		return this.realm;
	}

	public List<WampRole> getRoles() {
		return this.roles;
	}

	@Override
	public String toString() {
		return "HelloMessage [realm=" + this.realm + ", roles=" + this.roles + "]";
	}

}
