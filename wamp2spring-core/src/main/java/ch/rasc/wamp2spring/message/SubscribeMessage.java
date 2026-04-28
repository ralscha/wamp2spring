/*
 * Copyright the original author or authors.
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * A Subscriber subscribes to a topic with this message.
 *
 * [SUBSCRIBE, Request|id, Options|dict, Topic|uri]
 */
public class SubscribeMessage extends WampMessage {

	public static final int CODE = 32;

	private final long requestId;

	private final MatchPolicy matchPolicy;

	private final String topic;

	private final boolean getRetained;

	@Nullable private final String nkey;

	@Nullable private final Map<String, Object> options;

	public SubscribeMessage(long requestId, String topic) {
		this(requestId, topic, MatchPolicy.EXACT, false, null);
	}

	public SubscribeMessage(long requestId, String topic, boolean get_retained) {
		this(requestId, topic, MatchPolicy.EXACT, get_retained, null);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match) {
		this(requestId, topic, match, false, null);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match, boolean getRetained) {
		this(requestId, topic, match, getRetained, null);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match, boolean getRetained,
			@Nullable Map<String, Object> options) {
		this(requestId, topic, match, getRetained, null, options);
	}

	public SubscribeMessage(long requestId, String topic, MatchPolicy match, boolean getRetained, @Nullable String nkey,
			@Nullable Map<String, Object> options) {
		super(CODE);
		this.requestId = requestId;
		this.matchPolicy = match;
		this.topic = topic;
		this.getRetained = getRetained;
		this.nkey = nkey;
		if (options != null || nkey != null) {
			Map<String, Object> normalizedOptions = new LinkedHashMap<>();
			if (options != null) {
				normalizedOptions.putAll(options);
			}
			if (nkey != null) {
				normalizedOptions.put("nkey", nkey);
			}
			this.options = Collections.unmodifiableMap(normalizedOptions);
		}
		else {
			this.options = null;
		}
	}

	public static SubscribeMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		MatchPolicy match = MatchPolicy.EXACT;

		jp.nextToken();
		boolean getRetained = false;
		String nkey = null;
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			String extValue = (String) options.get("match");
			if (extValue != null) {
				match = MatchPolicy.fromExtValue(extValue);
				if (match == null) {
					match = MatchPolicy.EXACT;
				}
			}
			getRetained = (boolean) options.getOrDefault("get_retained", false);
			nkey = (String) options.get("nkey");
		}

		jp.nextToken();
		String topic = jp.getValueAsString();

		return new SubscribeMessage(request, topic, match, getRetained, nkey, options);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.matchPolicy != MatchPolicy.EXACT) {
			generator.writeStringProperty("match", this.matchPolicy.getExternalValue());
		}
		if (this.getRetained) {
			generator.writeBooleanProperty("get_retained", this.getRetained);
		}
		if (this.nkey != null) {
			generator.writeStringProperty("nkey", this.nkey);
		}
		generator.writeEndObject();

		generator.writeString(this.topic);
	}

	/**
	 * Returns a random ID chosen by the Subscriber and used to correlate the Broker's
	 * response with the request.
	 */
	public long getRequestId() {
		return this.requestId;
	}

	/**
	 * Returns the matching policy for this subscription.
	 */
	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	/**
	 * Returns the topic the Subscriber wants to subscribe to
	 */
	public String getTopic() {
		return this.topic;
	}

	/**
	 * Returns the get_retained flag. If true the Broker sends back a retained event, when
	 * available.
	 */
	public boolean isGetRetained() {
		return this.getRetained;
	}

	public @Nullable String getNkey() {
		return this.nkey;
	}

	/**
	 * Returns the Options dictionary. Third argument of a SUBSCRIBE message.
	 * <p>
	 * <code>[SUBSCRIBE, Request|id, Options|dict, Topic|uri]</code>
	 * <p>
	 * Returns a unmodifiable view of the map. Can be null.
	 */
	public @Nullable Map<String, Object> getOptions() {
		return this.options;
	}

	@Override
	public String toString() {
		return "SubscribeMessage [requestId=" + this.requestId + ", matchPolicy=" + this.matchPolicy + ", topic="
				+ this.topic + ", getRetained=" + this.getRetained + ", nkey=" + this.nkey + "]";
	}

}
