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
 * [AUTHENTICATE, Signature|string, Extra|dict]
 */
public class AuthenticateMessage extends WampMessage {

	static final int CODE = 5;

	private final String signature;

	private final Map<String, Object> extra;

	public AuthenticateMessage(String signature, @Nullable Map<String, Object> extra) {
		super(CODE);
		this.signature = signature;
		this.extra = extra != null ? Collections.unmodifiableMap(extra) : Map.of();
	}

	public static AuthenticateMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		String signature = jp.getValueAsString();

		jp.nextToken();
		Map<String, Object> extra = ParserUtil.readObject(jp);
		return new AuthenticateMessage(signature, extra);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeString(this.signature);
		generator.writeObject(this.extra);
	}

	public String getSignature() {
		return this.signature;
	}

	public Map<String, Object> getExtra() {
		return this.extra;
	}

	@Override
	public String toString() {
		return "AuthenticateMessage [signature=" + this.signature + ", extra=" + this.extra + "]";
	}

}