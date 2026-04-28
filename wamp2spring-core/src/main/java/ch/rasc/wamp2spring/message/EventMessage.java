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
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/**
 * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict] <br>
 * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict,
 * PUBLISH.Arguments|list] <br>
 * [EVENT, SUBSCRIBED.Subscription|id, PUBLISHED.Publication|id, Details|dict,
 * PUBLISH.Arguments|list, PUBLISH.ArgumentKw|dict]
 */
public class EventMessage extends WampMessage {

	public static final int CODE = 36;

	private final long subscriptionId;

	private final long publicationId;

	@Nullable private final String topic;

	@Nullable private final Number publisher;

	@Nullable private final String publisherAuthId;

	@Nullable private final String publisherAuthRole;

	@Nullable private final Number publisherTrustLevel;

	private final boolean retained;

	@Nullable private final List<Object> arguments;

	@Nullable private final Map<String, Object> argumentsKw;

	public EventMessage(long subscriptionId, long publicationId, @Nullable String topic, @Nullable Number publisher,
			boolean retained, @Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		this(subscriptionId, publicationId, topic, publisher, null, null, null, retained, arguments, argumentsKw);
	}

	public EventMessage(long subscriptionId, long publicationId, @Nullable String topic, @Nullable Number publisher,
			@Nullable String publisherAuthId, @Nullable String publisherAuthRole, @Nullable Number publisherTrustLevel,
			boolean retained, @Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.subscriptionId = subscriptionId;
		this.publicationId = publicationId;
		this.topic = topic;
		this.publisher = publisher;
		this.publisherAuthId = publisherAuthId;
		this.publisherAuthRole = publisherAuthRole;
		this.publisherTrustLevel = publisherTrustLevel;
		this.retained = retained;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public EventMessage(@Nullable String receiverWebSocketSessionId, long subscription, long publication,
			@Nullable String topic, @Nullable Number publisher, boolean retained, PublishMessage publishMessage) {
		this(subscription, publication, topic, publisher,
				publishMessage.isDiscloseMe() ? publishMessage.getAuthId() : null,
				publishMessage.isDiscloseMe() ? publishMessage.getAuthRole() : null,
				publishMessage.isDiscloseMe() ? publishMessage.getTrustLevel() : null, retained,
				publishMessage.getArguments(), publishMessage.getArgumentsKw());

		if (receiverWebSocketSessionId != null) {
			setReceiverWebSocketSessionId(receiverWebSocketSessionId);
		}
	}

	public static EventMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long subscription = jp.getLongValue();

		jp.nextToken();
		long publication = jp.getLongValue();

		jp.nextToken();
		String topic = null;
		Number publisher = null;
		String publisherAuthId = null;
		String publisherAuthRole = null;
		Number publisherTrustLevel = null;
		boolean retained = false;
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			topic = (String) details.get("topic");
			publisher = (Number) details.get("publisher");
			publisherAuthId = (String) details.get("publisher_authid");
			publisherAuthRole = (String) details.get("publisher_authrole");
			publisherTrustLevel = (Number) details.get("publisher_trustlevel");
			retained = (boolean) details.getOrDefault("retained", false);
		}

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

		return new EventMessage(subscription, publication, topic, publisher, publisherAuthId, publisherAuthRole,
				publisherTrustLevel, retained, arguments, argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		Number publisher = this.publisher;
		generator.writeNumber(getCode());
		generator.writeNumber(this.subscriptionId);
		generator.writeNumber(this.publicationId);

		generator.writeStartObject();
		if (this.topic != null) {
			generator.writeStringProperty("topic", this.topic);
		}
		if (publisher != null) {
			generator.writeNumberProperty("publisher", publisher.longValue());
		}
		if (this.publisherAuthId != null) {
			generator.writeStringProperty("publisher_authid", this.publisherAuthId);
		}
		if (this.publisherAuthRole != null) {
			generator.writeStringProperty("publisher_authrole", this.publisherAuthRole);
		}
		if (this.publisherTrustLevel != null) {
			generator.writeNumberProperty("publisher_trustlevel", this.publisherTrustLevel.longValue());
		}
		if (this.retained) {
			generator.writeBooleanProperty("retained", this.retained);
		}
		generator.writeEndObject();

		if (this.argumentsKw != null) {
			if (this.arguments == null) {
				generator.writeStartArray();
				generator.writeEndArray();
			}
			else {
				generator.writePOJO(this.arguments);
			}
			generator.writePOJO(this.argumentsKw);
		}
		else if (this.arguments != null) {
			generator.writePOJO(this.arguments);
		}
	}

	public long getSubscriptionId() {
		return this.subscriptionId;
	}

	public long getPublicationId() {
		return this.publicationId;
	}

	@Nullable public String getTopic() {
		return this.topic;
	}

	@Nullable public Number getPublisher() {
		return this.publisher;
	}

	@Nullable public String getPublisherAuthId() {
		return this.publisherAuthId;
	}

	@Nullable public String getPublisherAuthRole() {
		return this.publisherAuthRole;
	}

	@Nullable public Number getPublisherTrustLevel() {
		return this.publisherTrustLevel;
	}

	public boolean isRetained() {
		return this.retained;
	}

	@Nullable public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Override
	public String toString() {
		return "EventMessage [subscriptionId=" + this.subscriptionId + ", publicationId=" + this.publicationId
				+ ", topic=" + this.topic + ", publisher=" + this.publisher + ", publisherAuthId="
				+ this.publisherAuthId + ", publisherAuthRole=" + this.publisherAuthRole + ", publisherTrustLevel="
				+ this.publisherTrustLevel + ", retained=" + this.retained + ", arguments=" + this.arguments
				+ ", argumentsKw=" + this.argumentsKw + "]";
	}

}
