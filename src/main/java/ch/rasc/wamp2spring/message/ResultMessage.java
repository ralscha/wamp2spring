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
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * [RESULT, CALL.Request|id, Details|dict]
 *
 * [RESULT, CALL.Request|id, Details|dict, YIELD.Arguments|list]
 *
 * [RESULT, CALL.Request|id, Details|dict, YIELD.Arguments|list, YIELD.ArgumentsKw|dict]
 */
public class ResultMessage extends WampMessage {

	static final int CODE = 50;

	public final long requestId;

	@Nullable
	private final List<Object> arguments;

	@Nullable
	private final Map<String, Object> argumentsKw;

	public ResultMessage(long requestId, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.requestId = requestId;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public ResultMessage(CallMessage callMessage, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		this(callMessage.getRequestId(), arguments, argumentsKw);
		setReceiver(callMessage);
	}

	public ResultMessage(YieldMessage yieldMessage, CallMessage callMessage) {
		this(callMessage.getRequestId(), yieldMessage.getArguments(),
				yieldMessage.getArgumentsKw());
		setReceiver(callMessage);
	}

	public static ResultMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		ParserUtil.readObject(jp);

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

		return new ResultMessage(request, arguments, argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeStartObject();
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

	@Nullable
	public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable
	public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Override
	public String toString() {
		return "ResultMessage [requestId=" + this.requestId + ", arguments="
				+ this.arguments + ", argumentsKw=" + this.argumentsKw + "]";
	}

}
