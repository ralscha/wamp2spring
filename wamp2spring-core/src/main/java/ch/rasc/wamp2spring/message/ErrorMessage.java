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

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import ch.rasc.wamp2spring.WampError;

/**
 * [ERROR, REQUEST.Type|int, REQUEST.Request|id, Details|dict, Error|uri]
 *
 * [ERROR, REQUEST.Type|int, REQUEST.Request|id, Details|dict, Error|uri, Arguments|list]
 *
 * [ERROR, REQUEST.Type|int, REQUEST.Request|id, Details|dict, Error|uri, Arguments|list,
 * ArgumentsKw|dict]
 */
public class ErrorMessage extends WampMessage {

	static final int CODE = 8;

	private final int type;

	private final long requestId;

	private final String error;

	@Nullable
	private final List<Object> arguments;

	@Nullable
	private final Map<String, Object> argumentsKw;

	public ErrorMessage(int type, long requestId, String error,
			@Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.type = type;
		this.requestId = requestId;
		this.error = error;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public ErrorMessage(ErrorMessage errorMessage, CallMessage callMessage) {
		this(callMessage.getCode(), callMessage.getRequestId(), errorMessage.getError(),
				errorMessage.getArguments(), errorMessage.getArgumentsKw());
		setReceiver(callMessage);
	}

	public ErrorMessage(SubscribeMessage subscribeMessage, WampError error) {
		this(subscribeMessage.getCode(), subscribeMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(subscribeMessage);
	}

	public ErrorMessage(UnsubscribeMessage unsubscribeMessage, WampError error) {
		this(unsubscribeMessage.getCode(), unsubscribeMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(unsubscribeMessage);
	}

	public ErrorMessage(PublishMessage publishMessage, WampError error) {
		this(publishMessage.getCode(), publishMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(publishMessage);
	}

	public ErrorMessage(RegisterMessage registerMessage, WampError error) {
		this(registerMessage.getCode(), registerMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(registerMessage);
	}

	public ErrorMessage(UnregisterMessage unregisterMessage, WampError error) {
		this(unregisterMessage.getCode(), unregisterMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(unregisterMessage);
	}

	public ErrorMessage(CallMessage callMessage, WampError error) {
		this(callMessage.getCode(), callMessage.getRequestId(), error.getExternalValue(),
				null, null);
		setReceiver(callMessage);
	}

	public ErrorMessage(CallMessage callMessage, String error,
			@Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {

		this(callMessage.getCode(), callMessage.getRequestId(), error, arguments,
				argumentsKw);
		setReceiver(callMessage);
	}

	public ErrorMessage(InvocationMessage invocationMessage, WampError error) {
		this(invocationMessage.getCode(), invocationMessage.getRequestId(),
				error.getExternalValue(), null, null);
		setReceiver(invocationMessage);
	}

	public static ErrorMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		int type = jp.getIntValue();

		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		ParserUtil.readObject(jp);

		jp.nextToken();
		String error = jp.getValueAsString();

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

		return new ErrorMessage(type, request, error, arguments, argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.type);
		generator.writeNumber(this.requestId);
		generator.writeStartObject();
		generator.writeEndObject();
		generator.writeString(this.error);

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

	public int getType() {
		return this.type;
	}

	public long getRequestId() {
		return this.requestId;
	}

	public String getError() {
		return this.error;
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
		return "ErrorMessage [type=" + this.type + ", requestId=" + this.requestId
				+ ", error=" + this.error + ", arguments=" + this.arguments
				+ ", argumentsKw=" + this.argumentsKw + "]";
	}

}
