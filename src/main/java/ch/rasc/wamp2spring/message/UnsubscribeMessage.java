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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [UNSUBSCRIBE, Request|id, SUBSCRIBED.Subscription|id]
 */
public class UnsubscribeMessage extends WampMessage {

	static final int CODE = 34;

	private final long requestId;

	private final long subscriptionId;

	public UnsubscribeMessage(long requestId, long subscriptionId) {
		super(CODE);
		this.requestId = requestId;
		this.subscriptionId = subscriptionId;
	}

	public static UnsubscribeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long subscription = jp.getLongValue();

		return new UnsubscribeMessage(request, subscription);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeNumber(this.subscriptionId);
	}

	public long getRequestId() {
		return this.requestId;
	}

	public long getSubscriptionId() {
		return this.subscriptionId;
	}

	@Override
	public String toString() {
		return "UnsubscribeMessage [requestId=" + this.requestId + ", subscriptionId="
				+ this.subscriptionId + "]";
	}

}
