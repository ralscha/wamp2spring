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
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * [YIELD, INVOCATION.Request|id, Options|dict]
 *
 * [YIELD, INVOCATION.Request|id, Options|dict, Arguments|list]
 *
 * [YIELD, INVOCATION.Request|id, Options|dict, Arguments|list, ArgumentsKw|dict]
 *
 */
public class YieldMessage extends WampMessage {

	static final int CODE = 70;

	private final long requestId;

	private final boolean progress;

	@Nullable private final List<Object> arguments;

	@Nullable private final Map<String, Object> argumentsKw;

	public YieldMessage(long requestId, @Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		this(requestId, false, arguments, argumentsKw);
	}

	public YieldMessage(long requestId, boolean progress, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.requestId = requestId;
		this.progress = progress;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public static YieldMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		boolean progress = false;
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			progress = (boolean) options.getOrDefault("progress", false);
		}

		List<Object> arguments = null;
		JsonToken token = jp.nextToken();
		if (token == JsonToken.START_ARRAY) {
			arguments = ParserUtil.readArray(jp);
		}

		Map<String, Object> argumentsKw = null;
		token = jp.nextToken();
		if (token == JsonToken.START_OBJECT) {
			argumentsKw = ParserUtil.readObject(jp);
		}

		return new YieldMessage(request, progress, arguments, argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeStartObject();
		if (this.progress) {
			generator.writeBooleanField("progress", true);
		}
		generator.writeEndObject();

		if (this.argumentsKw != null) {
			if (this.arguments == null) {
				generator.writeStartArray();
				generator.writeEndArray();
			}
			else {
				generator.writeObject(this.arguments);
			}
			generator.writeObject(this.argumentsKw);
		}
		else if (this.arguments != null) {
			generator.writeObject(this.arguments);
		}
	}

	public long getRequestId() {
		return this.requestId;
	}

	public boolean isProgress() {
		return this.progress;
	}

	@Nullable public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Override
	public String toString() {
		return "YieldMessage [requestId=" + this.requestId + ", progress=" + this.progress + ", arguments="
				+ this.arguments + ", argumentsKw=" + this.argumentsKw + "]";
	}

}
