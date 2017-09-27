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
import com.fasterxml.jackson.core.JsonToken;

/**
 * [UNSUBSCRIBED, UNSUBSCRIBE.Request|id]
 *
 * [UNSUBSCRIBED, UNSUBSCRIBE.Request|id, Details|dict]
 */
public class UnsubscribedMessage extends WampMessage {

	static final int CODE = 35;

	private final long requestId;

	@Nullable
	private final Long subscriptionId;

	@Nullable
	private final String reason;

	public UnsubscribedMessage(long requestId) {
		this(requestId, null, null);
	}

	public UnsubscribedMessage(long requestId, @Nullable Long subscriptionId,
			@Nullable String reason) {
		super(CODE);
		this.requestId = requestId;
		this.subscriptionId = subscriptionId;
		this.reason = reason;
	}

	public UnsubscribedMessage(UnsubscribeMessage unsubscribeMessage) {
		this(unsubscribeMessage.getRequestId());
		setReceiver(unsubscribeMessage);
	}

	public static UnsubscribedMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		Long subscriptionId = null;
		String reason = null;

		JsonToken token = jp.nextToken();
		if (token == JsonToken.START_OBJECT) {
			Map<String, Object> details = ParserUtil.readObject(jp);
			reason = (String) details.get("reason");

			Object subscriptionObj = details.get("subscription");
			if (subscriptionObj != null) {
				subscriptionId = ((Number) subscriptionObj).longValue();
			}
		}

		return new UnsubscribedMessage(request, subscriptionId, reason);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		if (this.subscriptionId != null || this.reason != null) {
			generator.writeStartObject();
			if (this.reason != null) {
				generator.writeStringField("reason", this.reason);
			}
			if (this.subscriptionId != null) {
				generator.writeNumberField("subscription", this.subscriptionId);
			}
			generator.writeEndObject();
		}
	}

	public long getRequestId() {
		return this.requestId;
	}

	@Nullable
	public Long getSubscriptionId() {
		return this.subscriptionId;
	}

	@Nullable
	public String getReason() {
		return this.reason;
	}

	@Override
	public String toString() {
		return "UnsubscribedMessage [requestId=" + this.requestId + ", subscriptionId="
				+ this.subscriptionId + ", reason=" + this.reason + "]";
	}

}
