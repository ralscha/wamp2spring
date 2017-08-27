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
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

/**
 * [SUBSCRIBE, Request|id, Options|dict, Topic|uri]
 */
public class SubscribeMessage extends WampMessage {

	public static final int CODE = 32;

	private final long requestId;

	private final MatchPolicy matchPolicy;

	private final String topic;

	private final boolean getRetained;

	public SubscribeMessage(long requestId, String topic) {
		this(requestId, topic, MatchPolicy.EXACT, false);
	}

	public SubscribeMessage(long requestId, String topic, boolean get_retained) {
		this(requestId, topic, MatchPolicy.EXACT, get_retained);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match) {
		this(requestId, topic, match, false);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match,
			boolean getRetained) {
		super(CODE);
		this.requestId = requestId;
		this.matchPolicy = match;
		this.topic = topic;
		this.getRetained = getRetained;
	}

	public static SubscribeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		MatchPolicy match = MatchPolicy.EXACT;

		jp.nextToken();
		boolean getRetained = false;
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			String extValue = (String) options.get("match");
			if (extValue != null) {
				match = MatchPolicy.fromExtValue(extValue);
				if (match == null) {
					match = MatchPolicy.EXACT;
				}
			}
			if (options.containsKey("get_retained")) {
				getRetained = (boolean) options.get("get_retained");
			}
		}

		jp.nextToken();
		String topic = jp.getValueAsString();

		return new SubscribeMessage(request, topic, match, getRetained);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.matchPolicy != MatchPolicy.EXACT) {
			generator.writeStringField("match", this.matchPolicy.getExternalValue());
		}
		if (this.getRetained) {
			generator.writeBooleanField("get_retained", this.getRetained);
		}
		generator.writeEndObject();

		generator.writeString(this.topic);
	}

	public long getRequestId() {
		return this.requestId;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public String getTopic() {
		return this.topic;
	}

	public boolean isGetRetained() {
		return this.getRetained;
	}

	@Override
	public String toString() {
		return "SubscribeMessage [requestId=" + this.requestId + ", matchPolicy="
				+ this.matchPolicy + ", topic=" + this.topic + ", getRetained="
				+ this.getRetained + "]";
	}

}
