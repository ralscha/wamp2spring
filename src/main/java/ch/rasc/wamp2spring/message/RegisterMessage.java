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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [REGISTER, Request|id, Options|dict, Procedure|uri]
 */
public class RegisterMessage extends WampMessage {

	public static final int CODE = 64;

	private final long requestId;

	private final String procedure;

	private final boolean discloseCaller;

	public RegisterMessage(long requestId, String procedure) {
		this(requestId, procedure, false);
	}

	public RegisterMessage(long requestId, String procedure, boolean discloseCaller) {
		super(CODE);
		this.requestId = requestId;
		this.procedure = procedure;
		this.discloseCaller = discloseCaller;
	}

	public static RegisterMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		boolean discloseCaller = false;
		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			discloseCaller = (boolean) options.getOrDefault("disclose_caller", false);
		}

		jp.nextToken();
		String procedure = jp.getValueAsString();

		return new RegisterMessage(request, procedure, discloseCaller);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.discloseCaller) {
			generator.writeBooleanField("disclose_caller", this.discloseCaller);
		}
		generator.writeEndObject();

		generator.writeString(this.procedure);

	}

	public long getRequestId() {
		return this.requestId;
	}

	public String getProcedure() {
		return this.procedure;
	}

	public boolean isDiscloseCaller() {
		return this.discloseCaller;
	}

	@Override
	public String toString() {
		return "RegisterMessage [requestId=" + this.requestId + ", procedure="
				+ this.procedure + ", discloseCaller=" + this.discloseCaller + "]";
	}

}
