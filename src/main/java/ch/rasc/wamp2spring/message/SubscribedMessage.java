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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [SUBSCRIBED, SUBSCRIBE.Request|id, Subscription|id]
 */
public class SubscribedMessage extends WampMessage {

	static final int CODE = 33;

	private final long requestId;

	private final long subscriptionId;

	public SubscribedMessage(long requestId, long subscriptionId) {
		super(CODE);
		this.requestId = requestId;
		this.subscriptionId = subscriptionId;
	}

	public SubscribedMessage(SubscribeMessage subscribeMessage, long subscriptionId) {
		this(subscribeMessage.getRequestId(), subscriptionId);
		setReceiver(subscribeMessage);
	}

	public static SubscribedMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long subscription = jp.getLongValue();

		return new SubscribedMessage(request, subscription);
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
		return "SubscribedMessage [requestId=" + this.requestId + ", subscriptionId="
				+ this.subscriptionId + "]";
	}

}
