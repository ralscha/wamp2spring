/**
 * Copyright 2017-2021 the original author or authors.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [REGISTERED, REGISTER.Request|id, Registration|id]
 */
public class RegisteredMessage extends WampMessage {

	static final int CODE = 65;

	private final long requestId;

	private final long registrationId;

	public RegisteredMessage(long requestId, long registrationId) {
		super(CODE);
		this.requestId = requestId;
		this.registrationId = registrationId;
	}

	public RegisteredMessage(RegisterMessage registerMessage, long registrationId) {
		this(registerMessage.getRequestId(), registrationId);
		setReceiver(registerMessage);
	}

	public static RegisteredMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long registration = jp.getLongValue();

		return new RegisteredMessage(request, registration);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeNumber(this.registrationId);
	}

	public long getRequestId() {
		return this.requestId;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	@Override
	public String toString() {
		return "RegisteredMessage [requestId=" + this.requestId + ", registrationId="
				+ this.registrationId + "]";
	}

}
