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

import org.assertj.core.data.MapEntry;
import org.junit.Test;

public class PublishMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		PublishMessage publishMessage = PublishMessage.builder(1, "topic").build();

		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(1);
		assertThat(publishMessage.getTopic()).isEqualTo("topic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).isNull();
		assertThat(publishMessage.getArgumentsKw()).isNull();

		String json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo("[16,1,{},\"topic\"]");

		publishMessage = PublishMessage.builder(1, "topic")
				.arguments(Arrays.asList("abc", 23)).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(1);
		assertThat(publishMessage.getTopic()).isEqualTo("topic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly("abc", 23);
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo("[16,1,{},\"topic\",[\"abc\",23]]");

		publishMessage = PublishMessage.builder(1, "topic")
				.arguments(Collections.singletonMap("colors", "blue")).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(1);
		assertThat(publishMessage.getTopic()).isEqualTo("topic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).isNull();
		assertThat(publishMessage.getArgumentsKw())
				.containsExactly(MapEntry.entry("colors", "blue"));

		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo("[16,1,{},\"topic\",[],{\"colors\":\"blue\"}]");

		publishMessage = PublishMessage.builder(2, "event").discloseMe()
				.arguments(Arrays.asList(23)).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(2);
		assertThat(publishMessage.getTopic()).isEqualTo("event");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isTrue();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();
		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo("[16,2,{\"disclose_me\":true},\"event\",[23]]");

		publishMessage = PublishMessage.builder(2, "event").retain()
				.arguments(Arrays.asList(23)).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(2);
		assertThat(publishMessage.getTopic()).isEqualTo("event");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isTrue();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();
		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo("[16,2,{\"retain\":true},\"event\",[23]]");

		publishMessage = PublishMessage.builder(2, "event").notExcludeMe().acknowledge()
				.arguments(Arrays.asList(23)).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(2);
		assertThat(publishMessage.getTopic()).isEqualTo("event");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isTrue();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isFalse();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();
		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo(
				"[16,2,{\"acknowledge\":true,\"exclude_me\":false},\"event\",[23]]");

		publishMessage = PublishMessage.builder(2, "event").eligible(toSet(2L, 3L))
				.exclude(toSet(7891255L, 1245751L)).arguments(Arrays.asList(23)).build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(2);
		assertThat(publishMessage.getTopic()).isEqualTo("event");
		assertThat(publishMessage.getEligible()).containsOnly(2L, 3L);
		assertThat(publishMessage.getExclude()).containsOnly(7891255L, 1245751L);
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();
		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo(
				"[16,2,{\"exclude\":[1245751,7891255],\"eligible\":[2,3]},\"event\",[23]]");

		publishMessage = PublishMessage.builder(2, "event").addEligible(2).addEligible(3)
				.addExclude(7891255).addExclude(1245751).addArgument(23)
				.addArgument("color", "green").build();
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(2);
		assertThat(publishMessage.getTopic()).isEqualTo("event");
		assertThat(publishMessage.getEligible()).containsOnly(2, 3);
		assertThat(publishMessage.getExclude()).containsOnly(7891255, 1245751);
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw())
				.containsExactly(MapEntry.entry("color", "green"));
		json = serializeToJson(publishMessage);
		assertThat(json).isEqualTo(
				"[16,2,{\"exclude\":[1245751,7891255],\"eligible\":[2,3]},\"event\",[23],{\"color\":\"green\"}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[16, 239714735, {}, \"com.myapp.mytopic1\"]";

		PublishMessage publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(239714735L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic1");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).isNull();
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = "[16, 239714735, {}, \"com.myapp.mytopic1\", [\"Hello, world!\"]]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(239714735L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic1");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = "[16, 239714735, {}, \"com.myapp.mytopic1\", [], {\"color\": \"orange\",\n"
				+ "\"sizes\": [23, 42, 7]}]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(239714735L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic1");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).isEmpty();
		assertThat(publishMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("color", "orange"),
				MapEntry.entry("sizes", Arrays.asList(23, 42, 7)));

		json = "[16, 523412, {\"disclose_me\": true}, \"com.myapp.mytopic\", [23]]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(523412L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isTrue();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = "[16, 523412, {\"retain\": true}, \"com.myapp.mytopic\", [23]]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(523412L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isTrue();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = "[16, 523412, {\"exclude_me\": false,\"acknowledge\":true}, \"com.myapp.mytopic\", [23]]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(523412L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic");
		assertThat(publishMessage.getEligible()).isNull();
		assertThat(publishMessage.getExclude()).isNull();
		assertThat(publishMessage.isAcknowledge()).isTrue();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isFalse();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();

		json = "[16, 523412, {\"exclude\": [7891255,1245751],\"eligible\": [2,3]}, \"com.myapp.mytopic\", [23]]";
		publishMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isEqualTo(523412L);
		assertThat(publishMessage.getTopic()).isEqualTo("com.myapp.mytopic");
		assertThat(publishMessage.getEligible()).containsExactly(2, 3);
		assertThat(publishMessage.getExclude()).containsOnly(7891255, 1245751);
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isRetain()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactly(23);
		assertThat(publishMessage.getArgumentsKw()).isNull();
	}

}
