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
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

public class CallMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		CallMessage callMessage = new CallMessage(1, "call");
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(1);
		assertThat(callMessage.getProcedure()).isEqualTo("call");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).isNull();
		assertThat(callMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(callMessage);
		assertThat(json).isEqualTo("[48,1,{},\"call\"]");

		callMessage = new CallMessage(1, "call", Arrays.asList("Hello world"));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(1);
		assertThat(callMessage.getProcedure()).isEqualTo("call");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).containsExactly("Hello world");
		assertThat(callMessage.getArgumentsKw()).isNull();
		json = serializeToJson(callMessage);
		assertThat(json).isEqualTo("[48,1,{},\"call\",[\"Hello world\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		callMessage = new CallMessage(1, "call", Arrays.asList("johnny"), argumentsKw,
				false);
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(1);
		assertThat(callMessage.getProcedure()).isEqualTo("call");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).containsExactly("johnny");
		assertThat(callMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));
		json = serializeToJson(callMessage);
		assertThat(json).isEqualTo(
				"[48,1,{},\"call\",[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		callMessage = new CallMessage(1, "call", Arrays.asList("Hello world"), null,
				true);
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(1);
		assertThat(callMessage.getProcedure()).isEqualTo("call");
		assertThat(callMessage.isDiscloseMe()).isTrue();
		assertThat(callMessage.getArguments()).containsExactly("Hello world");
		assertThat(callMessage.getArgumentsKw()).isNull();
		json = serializeToJson(callMessage);
		assertThat(json)
				.isEqualTo("[48,1,{\"disclose_me\":true},\"call\",[\"Hello world\"]]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[48, 7814135, {}, \"com.myapp.ping\"]";

		CallMessage callMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(callMessage.getProcedure()).isEqualTo("com.myapp.ping");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).isNull();
		assertThat(callMessage.getArgumentsKw()).isNull();

		json = " [48, 7814135, {}, \"com.myapp.echo\", [\"Hello, world!\"]]";
		callMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(callMessage.getProcedure()).isEqualTo("com.myapp.echo");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(callMessage.getArgumentsKw()).isNull();

		json = "[48, 7814135, {}, \"com.myapp.add2\", [23, 7]]";
		callMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(callMessage.getProcedure()).isEqualTo("com.myapp.add2");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).containsExactly(23, 7);
		assertThat(callMessage.getArgumentsKw()).isNull();

		json = "[48, 7814135, {}, \"com.myapp.user.new\", [\"johnny\"],{\"firstname\": \"John\", \"surname\": \"Doe\"}]";
		callMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(callMessage.getProcedure()).isEqualTo("com.myapp.user.new");
		assertThat(callMessage.isDiscloseMe()).isFalse();
		assertThat(callMessage.getArguments()).containsExactly("johnny");
		assertThat(callMessage.getArgumentsKw()).containsExactly(
				MapEntry.entry("firstname", "John"), MapEntry.entry("surname", "Doe"));

		json = "[48, 7814135, {\"disclose_me\":true}, \"com.myapp.add2\", [23, 7]]";
		callMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(callMessage.getCode()).isEqualTo(48);
		assertThat(callMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(callMessage.getProcedure()).isEqualTo("com.myapp.add2");
		assertThat(callMessage.isDiscloseMe()).isTrue();
		assertThat(callMessage.getArguments()).containsExactly(23, 7);
		assertThat(callMessage.getArgumentsKw()).isNull();
	}

}
