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
package ch.rasc.wampspring.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.junit.Test;

public class YieldMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		YieldMessage yieldMessage = new YieldMessage(111, null, null);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{}]");

		yieldMessage = new YieldMessage(111, Arrays.asList("Hello world"), null);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.getArguments()).containsExactly("Hello world");
		assertThat(yieldMessage.getArgumentsKw()).isNull();
		json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{},[\"Hello world\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		yieldMessage = new YieldMessage(111, Arrays.asList("johnny"), argumentsKw);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.getArguments()).containsExactly("johnny");
		assertThat(yieldMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo(
				"[70,111,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		yieldMessage = new YieldMessage(111, null, argumentsKw);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(yieldMessage);
		assertThat(json)
				.isEqualTo("[70,111,{},[],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[70, 6131533, {}]";

		YieldMessage yieldMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [\"Hello, world!\"]]";
		yieldMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [30]]";
		yieldMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.getArguments()).containsExactly(30);
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [], {\"userid\": 123, \"karma\": 10}]";
		yieldMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.getArguments()).isEmpty();
		assertThat(yieldMessage.getArgumentsKw())
				.containsOnly(MapEntry.entry("userid", 123), MapEntry.entry("karma", 10));

		json = "[70, 6131533, {}, [\"a\",\"b\",\"c\"], {\"userid\": 123, \"karma\": 10}]";
		yieldMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.getArguments()).containsExactly("a", "b", "c");
		assertThat(yieldMessage.getArgumentsKw())
				.containsOnly(MapEntry.entry("userid", 123), MapEntry.entry("karma", 10));
	}

}
