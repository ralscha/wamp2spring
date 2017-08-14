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
package ch.rasc.wampspring.message;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [REGISTER, Request|id, Options|dict, Procedure|uri]
 */
public class RegisterMessage extends WampMessage {

	static final int CODE = 64;

	private final long requestId;

	private final String procedure;

	public RegisterMessage(long requestId, String procedure) {
		super(CODE);
		this.requestId = requestId;
		this.procedure = procedure;
	}

	public static RegisterMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		ParserUtil.readObject(jp);

		jp.nextToken();
		String procedure = jp.getValueAsString();

		return new RegisterMessage(request, procedure);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		generator.writeEndObject();

		generator.writeString(this.procedure);

	}

	public long getRequestId() {
		return this.requestId;
	}

	public String getProcedure() {
		return this.procedure;
	}

	@Override
	public String toString() {
		return "RegisterMessage [requestId=" + this.requestId + ", procedure="
				+ this.procedure + "]";
	}

}
