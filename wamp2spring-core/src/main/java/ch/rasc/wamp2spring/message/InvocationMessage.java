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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import ch.rasc.wamp2spring.rpc.Procedure;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict]
 *
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list]
 *
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list,
 * CALL.ArgumentsKw|dict]
 */
public class InvocationMessage extends WampMessage {

	private static final AtomicLong lastRequest = new AtomicLong(1L);

	public static final int CODE = 68;

	private final long requestId;

	private final long registrationId;

	@Nullable
	private final List<Object> arguments;

	@Nullable
	private final Number caller;

	@Nullable
	private final Map<String, Object> argumentsKw;

	public InvocationMessage(long requestId, long registrationId, @Nullable Number caller,
			@Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.requestId = requestId;
		this.registrationId = registrationId;
		this.caller = caller;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public InvocationMessage(Procedure procedure, CallMessage callMessage) {
		this(IdGenerator.newLinearId(lastRequest), procedure.getRegistrationId(),
				procedure.isDiscloseCaller() || callMessage.isDiscloseMe()
						? callMessage.getWampSessionId()
						: null,
				callMessage.getArguments(), callMessage.getArgumentsKw());
		setReceiverWebSocketSessionId(procedure.getWebSocketSessionId());
	}

	public static InvocationMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long registration = jp.getLongValue();

		jp.nextToken();
		Number caller = null;
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			caller = (Number) details.get("caller");
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

		return new InvocationMessage(request, registration, caller, arguments,
				argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeNumber(this.registrationId);
		generator.writeStartObject();
		if (this.caller != null) {
			generator.writeNumberField("caller", this.caller.longValue());
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

	public long getRegistrationId() {
		return this.registrationId;
	}

	@Nullable
	public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable
	public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Nullable
	public Number getCaller() {
		return this.caller;
	}

	@Override
	public String toString() {
		return "InvocationMessage [requestId=" + this.requestId + ", registrationId="
				+ this.registrationId + ", arguments=" + this.arguments + ", caller="
				+ this.caller + ", argumentsKw=" + this.argumentsKw + "]";
	}

}
