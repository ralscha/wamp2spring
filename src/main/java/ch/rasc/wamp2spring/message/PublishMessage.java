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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import ch.rasc.wamp2spring.util.CollectionHelper;

/**
 * [PUBLISH, Request|id, Options|dict, Topic|uri]
 *
 * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list]
 *
 * [PUBLISH, Request|id, Options|dict, Topic|uri, Arguments|list, ArgumentsKw|dict]
 */
public class PublishMessage extends WampMessage {

	static final int CODE = 16;

	private final long requestId;

	private final boolean acknowledge;

	private final boolean excludeMe;

	private final boolean discloseMe;

	private final String topic;

	@Nullable
	private final Set<Number> exclude;

	@Nullable
	private final Set<Number> eligible;

	@Nullable
	private final List<Object> arguments;

	@Nullable
	private final Map<String, Object> argumentsKw;

	private PublishMessage(long requestId, String topic, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw, boolean acknowledge,
			boolean excludeMe, boolean discloseMe, @Nullable Set<Number> exclude,
			@Nullable Set<Number> eligible) {
		super(CODE);
		this.requestId = requestId;
		this.topic = topic;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
		this.acknowledge = acknowledge;
		this.excludeMe = excludeMe;
		this.discloseMe = discloseMe;
		this.exclude = exclude;
		this.eligible = eligible;
	}

	PublishMessage(Builder builder) {
		this(builder.request, builder.topic, builder.arguments, builder.argumentsKw,
				builder.acknowledge, builder.excludeMe, builder.discloseMe,
				builder.exclude, builder.eligible);
	}

	public static Builder builder(long request, String topic) {
		return new Builder(request, topic);
	}

	public static class Builder {
		long request;

		boolean excludeMe;

		String topic;

		boolean discloseMe;

		boolean acknowledge;

		@Nullable
		List<Object> arguments;

		@Nullable
		Map<String, Object> argumentsKw;

		@Nullable
		Set<Number> exclude;

		@Nullable
		Set<Number> eligible;

		public Builder(long request, String topic) {
			this.request = request;
			this.topic = topic;
			this.excludeMe = true;
		}

		public Builder notExcludeMe() {
			this.excludeMe = false;
			return this;
		}

		public Builder discloseMe() {
			this.discloseMe = true;
			return this;
		}

		public Builder acknowledge() {
			this.acknowledge = true;
			return this;
		}

		public <T> Builder arguments(@Nullable Collection<T> args) {
			this.arguments = CollectionHelper.toList(args);
			return this;
		}

		@SuppressWarnings("unchecked")
		public <T> Builder arguments(@Nullable Map<String, T> argsKw) {
			this.argumentsKw = (Map<String, Object>) argsKw;
			return this;
		}

		public Builder addArgument(Object arg) {
			if (this.arguments == null) {
				this.arguments = new ArrayList<>();
			}
			this.arguments.add(arg);
			return this;
		}

		public Builder addArgument(String key, Object arg) {
			if (this.argumentsKw == null) {
				this.argumentsKw = new HashMap<>();
			}
			this.argumentsKw.put(key, arg);
			return this;
		}

		public Builder exclude(Collection<Long> excludeWampSessionIds) {
			this.exclude = excludeWampSessionIds.stream().map(l -> (Number) l)
					.collect(Collectors.toSet());
			return this;
		}

		public Builder eligible(Collection<Long> eligibleWampSessionIds) {
			this.eligible = eligibleWampSessionIds.stream().map(l -> (Number) l)
					.collect(Collectors.toSet());
			return this;
		}

		public Builder addExclude(Number excludeWampSessionId) {
			if (this.exclude == null) {
				this.exclude = new HashSet<>();
			}
			this.exclude.add(excludeWampSessionId);
			return this;
		}

		public Builder addEligible(Number eligibleWampSessionId) {
			if (this.eligible == null) {
				this.eligible = new HashSet<>();
			}
			this.eligible.add(eligibleWampSessionId);
			return this;
		}

		public PublishMessage build() {
			return new PublishMessage(this);
		}
	}

	@SuppressWarnings("unchecked")
	public static PublishMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		boolean acknowledge = false;
		boolean excludeMe = true;
		boolean discloseMe = false;
		Set<Number> exclude = null;
		Set<Number> eligible = null;
		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			if (options.containsKey("acknowledge")) {
				acknowledge = (boolean) options.get("acknowledge");
			}

			if (options.containsKey("exclude_me")) {
				excludeMe = (boolean) options.get("exclude_me");
			}

			if (options.containsKey("disclose_me")) {
				discloseMe = (boolean) options.get("disclose_me");
			}

			if (options.containsKey("exclude")) {
				exclude = new HashSet<>((List<Number>) options.get("exclude"));
			}

			if (options.containsKey("eligible")) {
				eligible = new HashSet<>((List<Number>) options.get("eligible"));
			}
		}

		jp.nextToken();
		String topic = jp.getValueAsString();

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

		return new PublishMessage(request, topic, arguments, argumentsKw, acknowledge,
				excludeMe, discloseMe, exclude, eligible);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.acknowledge) {
			generator.writeBooleanField("acknowledge", this.acknowledge);
		}

		if (!this.excludeMe) {
			generator.writeBooleanField("exclude_me", this.excludeMe);
		}

		if (this.discloseMe) {
			generator.writeBooleanField("disclose_me", this.discloseMe);
		}

		if (this.exclude != null) {
			generator.writeObjectField("exclude", this.exclude);
		}

		if (this.eligible != null) {
			generator.writeObjectField("eligible", this.eligible);
		}

		generator.writeEndObject();
		generator.writeString(this.topic);

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

	public boolean isAcknowledge() {
		return this.acknowledge;
	}

	public boolean isExcludeMe() {
		return this.excludeMe;
	}

	public boolean isDiscloseMe() {
		return this.discloseMe;
	}

	public String getTopic() {
		return this.topic;
	}

	@Nullable
	public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable
	public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Nullable
	public Set<Number> getExclude() {
		return this.exclude;
	}

	@Nullable
	public Set<Number> getEligible() {
		return this.eligible;
	}

	@Override
	public String toString() {
		return "PublishMessage [requestId=" + this.requestId + ", acknowledge="
				+ this.acknowledge + ", excludeMe=" + this.excludeMe + ", discloseMe="
				+ this.discloseMe + ", topic=" + this.topic + ", exclude=" + this.exclude
				+ ", eligible=" + this.eligible + ", arguments=" + this.arguments
				+ ", argumentsKw=" + this.argumentsKw + "]";
	}

}
