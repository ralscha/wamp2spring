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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.junit.Test;

public class EventMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		EventMessage eventMessage = new EventMessage(1, 2, null, null, false, null, null);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).isNull();
		assertThat(eventMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(eventMessage);
		assertThat(json).isEqualTo("[36,1,2,{}]");

		eventMessage = new EventMessage(1, 2, null, null, false,
				Collections.singletonList("Hello, world!"), null);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(eventMessage.getArgumentsKw()).isNull();
		json = serializeToJson(eventMessage);
		assertThat(json).isEqualTo("[36,1,2,{},[\"Hello, world!\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		eventMessage = new EventMessage(1, 2, null, null, false, Arrays.asList("johnny"),
				argumentsKw);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("johnny");
		assertThat(eventMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(eventMessage);
		assertThat(json).isEqualTo(
				"[36,1,2,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		eventMessage = new EventMessage(1, 2, null, null, false, null, argumentsKw);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).isNull();
		assertThat(eventMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(eventMessage);
		assertThat(json)
				.isEqualTo("[36,1,2,{},[],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		eventMessage = new EventMessage(1, 2, "topic", null, false,
				Collections.singletonList(42), null);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isEqualTo("topic");
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly(42);
		assertThat(eventMessage.getArgumentsKw()).isNull();
		json = serializeToJson(eventMessage);
		assertThat(json).isEqualTo("[36,1,2,{\"topic\":\"topic\"},[42]]");

		eventMessage = new EventMessage(1, 2, "topic", 123, false,
				Collections.singletonList(42), null);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isEqualTo("topic");
		assertThat(eventMessage.getPublisher()).isEqualTo(123);
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly(42);
		assertThat(eventMessage.getArgumentsKw()).isNull();
		json = serializeToJson(eventMessage);
		assertThat(json)
				.isEqualTo("[36,1,2,{\"topic\":\"topic\",\"publisher\":123},[42]]");

		eventMessage = new EventMessage(1, 2, null, null, true,
				Collections.singletonList(43), null);
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(1);
		assertThat(eventMessage.getPublicationId()).isEqualTo(2);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isTrue();
		assertThat(eventMessage.getArguments()).containsExactly(43);
		assertThat(eventMessage.getArgumentsKw()).isNull();
		json = serializeToJson(eventMessage);
		assertThat(json).isEqualTo("[36,1,2,{\"retained\":true},[43]]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[36, 5512315355, 4429313566, {}]";
		EventMessage eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).isNull();
		assertThat(eventMessage.getArgumentsKw()).isNull();

		json = "[36, 5512315355, 4429313566, {}, [\"Hello, world!\"]]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(eventMessage.getArgumentsKw()).isNull();

		json = "[36, 5512315355, 4429313566, {}, [], {\"color\": \"orange\",\"sizes\": [23, 42, 7]}]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).isEmpty();
		assertThat(eventMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("color", "orange"),
				MapEntry.entry("sizes", Arrays.asList(23, 42, 7)));

		json = "[36, 5512315355, 4429313566, {}, [\"a\",\"b\"], {\"color\": \"orange\",\"sizes\": [23, 42, 7]}]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("a", "b");
		assertThat(eventMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("color", "orange"),
				MapEntry.entry("sizes", Arrays.asList(23, 42, 7)));

		json = "[36, 5512315355, 4429313566, {\"topic\":\"the_topic\"}, [\"Hello, world!\"]]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isEqualTo("the_topic");
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(eventMessage.getArgumentsKw()).isNull();

		json = "[36, 5512315355, 4429313566, {\"topic\":\"the_topic\", \"publisher\":1234}, [\"Hello, world!\"]]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isEqualTo("the_topic");
		assertThat(eventMessage.getPublisher()).isEqualTo(1234);
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(eventMessage.getArgumentsKw()).isNull();

		json = "[36, 5512315355, 4429313566, {\"retained\":false}, [\"Not Retained\"]]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isFalse();
		assertThat(eventMessage.getArguments()).containsExactly("Not Retained");
		assertThat(eventMessage.getArgumentsKw()).isNull();

		json = "[36, 5512315355, 4429313566, {\"retained\":true}, [\"Retained\"]]";
		eventMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(eventMessage.getCode()).isEqualTo(36);
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(5512315355L);
		assertThat(eventMessage.getPublicationId()).isEqualTo(4429313566L);
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getPublisher()).isNull();
		assertThat(eventMessage.isRetained()).isTrue();
		assertThat(eventMessage.getArguments()).containsExactly("Retained");
		assertThat(eventMessage.getArgumentsKw()).isNull();
	}

}
