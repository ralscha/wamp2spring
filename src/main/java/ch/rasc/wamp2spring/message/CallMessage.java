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
 * [CALL, Request|id, Options|dict, Procedure|uri]
 *
 * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list]
 *
 * [CALL, Request|id, Options|dict, Procedure|uri, Arguments|list, ArgumentsKw|dict]
 */
public class CallMessage extends WampMessage {

	public static final int CODE = 48;

	private final long requestId;

	private final String procedure;

	private final boolean discloseMe;

	@Nullable
	private final List<Object> arguments;

	@Nullable
	private final Map<String, Object> argumentsKw;

	public CallMessage(long request, String procedure) {
		this(request, procedure, null, null, false);
	}

	public CallMessage(long request, String procedure, @Nullable List<Object> arguments) {
		this(request, procedure, arguments, null, false);
	}

	public CallMessage(long request, String procedure,
			@Nullable Map<String, Object> argumentsKw) {
		this(request, procedure, null, argumentsKw, false);
	}

	public CallMessage(long requestId, String procedure, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw, boolean discloseMe) {
		super(CODE);
		this.requestId = requestId;
		this.procedure = procedure;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
		this.discloseMe = discloseMe;
	}

	public static CallMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		boolean discloseMe = false;
		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			discloseMe = (boolean) options.getOrDefault("disclose_me", false);
		}

		jp.nextToken();
		String procedure = jp.getValueAsString();

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

		return new CallMessage(request, procedure, arguments, argumentsKw, discloseMe);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.discloseMe) {
			generator.writeBooleanField("disclose_me", this.discloseMe);
		}
		generator.writeEndObject();

		generator.writeString(this.procedure);

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

	public String getProcedure() {
		return this.procedure;
	}

	@Nullable
	public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable
	public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	public boolean isDiscloseMe() {
		return this.discloseMe;
	}

	@Override
	public String toString() {
		return "CallMessage [requestId=" + this.requestId + ", procedure="
				+ this.procedure + ", discloseMe=" + this.discloseMe + ", arguments="
				+ this.arguments + ", argumentsKw=" + this.argumentsKw + "]";
	}

}
