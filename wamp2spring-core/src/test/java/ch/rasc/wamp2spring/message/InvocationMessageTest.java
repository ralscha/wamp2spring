/**
 * Copyright the original author or authors.
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
import org.junit.jupiter.api.Test;

public class InvocationMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		InvocationMessage invocationMessage = new InvocationMessage(111, 1, null, null,
				null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(111);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(1);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).isNull();
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,111,1,{}]");

		invocationMessage = new InvocationMessage(1, 111, null,
				Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("Hello world");
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{},[\"Hello world\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		invocationMessage = new InvocationMessage(1, 111, null, Arrays.asList("johnny"),
				argumentsKw);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("johnny");
		assertThat(invocationMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo(
				"[68,1,111,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		invocationMessage = new InvocationMessage(1, 111, 17218L,
				Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isEqualTo(17218L);
		assertThat(invocationMessage.getArguments()).containsExactly("Hello world");
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{\"caller\":17218},[\"Hello world\"]]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[68, 6131533, 9823526, {}]";

		InvocationMessage invocationMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823526L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).isNull();
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823527, {}, [\"Hello, world!\"]]";
		invocationMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823527L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823528, {}, [23, 7]]";
		invocationMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823529, {}, [\"johnny\"], {\"firstname\": \"John\",\"surname\": \"Doe\"}]";
		invocationMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823529L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("johnny");
		assertThat(invocationMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));

		json = "[68, 6131533, 9823528, {\"caller\": 3335656}, [23, 7]]";
		invocationMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isEqualTo(3335656);
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();
	}

}
