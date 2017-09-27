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
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import ch.rasc.wamp2spring.WampError;

/**
 * [ABORT, Details|dict, Reason|uri]
 */
public class AbortMessage extends WampMessage {

	static final int CODE = 3;

	@Nullable
	private final String message;

	@Nullable
	private final String reason;

	public AbortMessage(WampError reason, @Nullable String message) {
		this(reason.getExternalValue(), message);
	}

	public AbortMessage(WampError reason) {
		this(reason.getExternalValue(), null);
	}

	private AbortMessage(String reason, @Nullable String message) {
		super(CODE);
		this.reason = reason;
		this.message = message;
	}

	public static AbortMessage deserialize(JsonParser jp) throws IOException {
		String msg = null;

		jp.nextToken();
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			msg = (String) details.get("message");
		}

		jp.nextToken();
		return new AbortMessage(jp.getValueAsString(), msg);
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

	@Nullable
	public String getMessage() {
		return this.message;
	}

	@Nullable
	public String getReason() {
		return this.reason;
	}

	@Override
	public String toString() {
		return "AbortMessage [message=" + this.message + ", reason=" + this.reason + "]";
	}

}
