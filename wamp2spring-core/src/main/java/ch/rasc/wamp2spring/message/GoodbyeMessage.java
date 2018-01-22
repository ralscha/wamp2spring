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
package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.util.Map;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import ch.rasc.wamp2spring.WampError;

/**
 * [GOODBYE, Details|dict, Reason|uri]
 */
public class GoodbyeMessage extends WampMessage {

	static final int CODE = 6;

	@Nullable
	private final String message;

	private final String reason;

	public GoodbyeMessage(WampError reason, @Nullable String message) {
		this(reason.getExternalValue(), message);
	}

	public GoodbyeMessage(WampError reason) {
		this(reason.getExternalValue(), null);
	}

	private GoodbyeMessage(String reason, @Nullable String message) {
		super(CODE);
		this.message = message;
		this.reason = reason;
	}

	public static GoodbyeMessage deserialize(JsonParser jp) throws IOException {
		String msg = null;

		jp.nextToken();
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			msg = (String) details.get("message");
		}

		jp.nextToken();
		return new GoodbyeMessage(jp.getValueAsString(), msg);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeStartObject();
		if (this.message != null) {
			generator.writeStringField("message", this.message);
		}
		generator.writeEndObject();
		generator.writeString(this.reason);
	}

	public String getReason() {
		return this.reason;
	}

	@Nullable
	public String getMessage() {
		return this.message;
	}

	@Override
	public String toString() {
		return "GoodbyeMessage [message=" + this.message + ", reason=" + this.reason
				+ "]";
	}

}
