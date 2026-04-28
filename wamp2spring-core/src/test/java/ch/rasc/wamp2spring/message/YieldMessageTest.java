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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

public class YieldMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		YieldMessage yieldMessage = new YieldMessage(111, null, null);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{}]");

		yieldMessage = new YieldMessage(111, Arrays.asList("Hello world"), null);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.isProgress()).isFalse();
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
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).containsExactly("johnny");
		assertThat(yieldMessage.getArgumentsKw()).containsExactly(MapEntry.entry("firstname", "John"),
				MapEntry.entry("surname", "Doe"));
		json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		yieldMessage = new YieldMessage(111, null, argumentsKw);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(111);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).containsExactly(MapEntry.entry("firstname", "John"),
				MapEntry.entry("surname", "Doe"));
		json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{},[],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		yieldMessage = new YieldMessage(111, true, Arrays.asList("partial"), null);
		assertThat(yieldMessage.isProgress()).isTrue();
		json = serializeToJson(yieldMessage);
		assertThat(json).isEqualTo("[70,111,{\"progress\":true},[\"partial\"]]");

	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[70, 6131533, {}]";

		YieldMessage yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).isNull();
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [\"Hello, world!\"]]";
		yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [30]]";
		yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).containsExactly(30);
		assertThat(yieldMessage.getArgumentsKw()).isNull();

		json = "[70, 6131533, {}, [], {\"userid\": 123, \"karma\": 10}]";
		yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).isEmpty();
		assertThat(yieldMessage.getArgumentsKw()).containsOnly(MapEntry.entry("userid", 123),
				MapEntry.entry("karma", 10));

		json = "[70, 6131533, {}, [\"a\",\"b\",\"c\"], {\"userid\": 123, \"karma\": 10}]";
		yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.getCode()).isEqualTo(70);
		assertThat(yieldMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(yieldMessage.isProgress()).isFalse();
		assertThat(yieldMessage.getArguments()).containsExactly("a", "b", "c");
		assertThat(yieldMessage.getArgumentsKw()).containsOnly(MapEntry.entry("userid", 123),
				MapEntry.entry("karma", 10));

		json = "[70, 6131533, {\"progress\": true}, [30]]";
		yieldMessage = deserializeYieldMessage(json);
		assertThat(yieldMessage.isProgress()).isTrue();
	}

	private YieldMessage deserializeYieldMessage(String json) throws IOException {
		return Objects.requireNonNull(WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8)));
	}

}
