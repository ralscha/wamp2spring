/**
 * Copyright 2017-2018 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.junit.Test;

public class ResultMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		ResultMessage resultMessage = new ResultMessage(111, null, null);
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(111);
		assertThat(resultMessage.getArguments()).isNull();
		assertThat(resultMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(resultMessage);
		assertThat(json).isEqualTo("[50,111,{}]");

		resultMessage = new ResultMessage(111, Arrays.asList("Hello world"), null);
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(111);
		assertThat(resultMessage.getArguments()).containsExactly("Hello world");
		assertThat(resultMessage.getArgumentsKw()).isNull();
		json = serializeToJson(resultMessage);
		assertThat(json).isEqualTo("[50,111,{},[\"Hello world\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		resultMessage = new ResultMessage(111, Arrays.asList("johnny"), argumentsKw);
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(111);
		assertThat(resultMessage.getArguments()).containsExactly("johnny");
		assertThat(resultMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(resultMessage);
		assertThat(json).isEqualTo(
				"[50,111,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		resultMessage = new ResultMessage(111, null, argumentsKw);
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(111);
		assertThat(resultMessage.getArguments()).isNull();
		assertThat(resultMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(resultMessage);
		assertThat(json)
				.isEqualTo("[50,111,{},[],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[50, 7814135, {}]";

		ResultMessage resultMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(resultMessage.getArguments()).isNull();
		assertThat(resultMessage.getArgumentsKw()).isNull();

		json = "[50, 7814135, {}, [\"Hello, world!\"]]";
		resultMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(resultMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(resultMessage.getArgumentsKw()).isNull();

		json = "[50, 7814135, {}, [30]]";
		resultMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(resultMessage.getArguments()).containsExactly(30);
		assertThat(resultMessage.getArgumentsKw()).isNull();

		json = "[50, 7814135, {}, [], {\"userid\": 123, \"karma\": 10}]";
		resultMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(resultMessage.getArguments()).isEmpty();
		assertThat(resultMessage.getArgumentsKw())
				.containsOnly(MapEntry.entry("userid", 123), MapEntry.entry("karma", 10));

		json = "[50, 7814135, {}, [\"a\",\"b\",\"c\"], {\"userid\": 123, \"karma\": 10}]";
		resultMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(resultMessage.getCode()).isEqualTo(50);
		assertThat(resultMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(resultMessage.getArguments()).containsExactly("a", "b", "c");
		assertThat(resultMessage.getArgumentsKw())
				.containsOnly(MapEntry.entry("userid", 123), MapEntry.entry("karma", 10));
	}

}
