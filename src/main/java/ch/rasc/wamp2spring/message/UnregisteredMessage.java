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

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * [UNREGISTERED, UNREGISTER.Request|id]
 *
 * [UNREGISTERED, UNREGISTER.Request|id, Details|dict]
 */
public class UnregisteredMessage extends WampMessage {

	static final int CODE = 67;

	private final long requestId;

	@Nullable
	private final Long registrationId;

	@Nullable
	private final String reason;

	public UnregisteredMessage(long requestId) {
		this(requestId, null, null);
	}

	public UnregisteredMessage(long requestId, @Nullable Long registrationId,
			@Nullable String reason) {
		super(CODE);
		this.requestId = requestId;
		this.registrationId = registrationId;
		this.reason = reason;
	}

	public UnregisteredMessage(UnregisterMessage unregisterMessage) {
		this(unregisterMessage.getRequestId());
		setReceiver(unregisterMessage);
	}

	public static UnregisteredMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		Long registrationId = null;
		String reason = null;

		JsonToken token = jp.nextToken();
		if (token == JsonToken.START_OBJECT) {
			Map<String, Object> details = ParserUtil.readObject(jp);
			reason = (String) details.get("reason");

			Object registrationObj = details.get("registration");
			if (registrationObj != null) {
				registrationId = ((Number) registrationObj).longValue();
			}
		}

		return new UnregisteredMessage(request, registrationId, reason);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		if (this.registrationId != null || this.reason != null) {
			generator.writeStartObject();
			if (this.reason != null) {
				generator.writeStringField("reason", this.reason);
			}
			if (this.registrationId != null) {
				generator.writeNumberField("registration", this.registrationId);
			}
			generator.writeEndObject();
		}
	}

	public long getRequestId() {
		return this.requestId;
	}

	@Nullable
	public Long getRegistrationId() {
		return this.registrationId;
	}

	@Nullable
	public String getReason() {
		return this.reason;
	}

	@Override
	public String toString() {
		return "UnregisteredMessage [requestId=" + this.requestId + ", registrationId="
				+ this.registrationId + ", reason=" + this.reason + "]";
	}

}
