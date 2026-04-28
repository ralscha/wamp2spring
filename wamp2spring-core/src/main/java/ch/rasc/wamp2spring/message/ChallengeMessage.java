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
import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [CHALLENGE, AuthMethod|string, Extra|dict]
 */
public class ChallengeMessage extends WampMessage {

	static final int CODE = 4;

	private final String authMethod;

	private final Map<String, Object> extra;

	public ChallengeMessage(String authMethod, @Nullable Map<String, Object> extra) {
		super(CODE);
		this.authMethod = authMethod;
		this.extra = extra != null ? Collections.unmodifiableMap(extra) : Map.of();
	}

	public static ChallengeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		String authMethod = jp.getValueAsString();

		jp.nextToken();
		Map<String, Object> extra = ParserUtil.readObject(jp);
		return new ChallengeMessage(authMethod, extra);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeString(this.authMethod);
		generator.writeObject(this.extra);
	}

	@Override
	public String getAuthMethod() {
		return this.authMethod;
	}

	public Map<String, Object> getExtra() {
		return this.extra;
	}

	@Override
	public String toString() {
		return "ChallengeMessage [authMethod=" + this.authMethod + ", extra=" + this.extra + "]";
	}

}