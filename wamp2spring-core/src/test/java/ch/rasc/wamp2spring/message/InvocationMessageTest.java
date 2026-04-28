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

public class InvocationMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		InvocationMessage invocationMessage = new InvocationMessage(111, 1, null, null, null, null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(111);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(1);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).isNull();
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,111,1,{}]");

		invocationMessage = new InvocationMessage(1, 111, null, null, Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("Hello world");
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{},[\"Hello world\"]]");

		Map<String, Object> argumentsKw = new HashMap<>();
		argumentsKw.put("firstname", "John");
		argumentsKw.put("surname", "Doe");
		invocationMessage = new InvocationMessage(1, 111, null, null, Arrays.asList("johnny"), argumentsKw);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("johnny");
		assertThat(invocationMessage.getArgumentsKw()).containsExactly(MapEntry.entry("firstname", "John"),
				MapEntry.entry("surname", "Doe"));
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{},[\"johnny\"],{\"firstname\":\"John\",\"surname\":\"Doe\"}]");

		invocationMessage = new InvocationMessage(1, 111, 17218L, null, Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(1);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(111);
		assertThat(invocationMessage.getCaller()).isEqualTo(17218L);
		assertThat(invocationMessage.getCallerAuthId()).isNull();
		assertThat(invocationMessage.getCallerAuthRole()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("Hello world");
		assertThat(invocationMessage.getArgumentsKw()).isNull();
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{\"caller\":17218},[\"Hello world\"]]");

		invocationMessage = new InvocationMessage(1, 111, 17218L, "caller1", "admin", null,
				Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getCallerAuthId()).isEqualTo("caller1");
		assertThat(invocationMessage.getCallerAuthRole()).isEqualTo("admin");
		assertThat(invocationMessage.getCallerTrustLevel()).isNull();
		assertThat(invocationMessage.getProcedure()).isNull();
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo(
				"[68,1,111,{\"caller\":17218,\"caller_authid\":\"caller1\",\"caller_authrole\":\"admin\"},[\"Hello world\"]]");

		invocationMessage = new InvocationMessage(1, 111, 17218L, "com.myapp.orders.create", "caller1", "admin", null,
				false, null, Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getProcedure()).isEqualTo("com.myapp.orders.create");
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo(
				"[68,1,111,{\"caller\":17218,\"procedure\":\"com.myapp.orders.create\",\"caller_authid\":\"caller1\",\"caller_authrole\":\"admin\"},[\"Hello world\"]]");

		invocationMessage = new InvocationMessage(1, 111, 17218L, 5000L, Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.getTimeout()).isEqualTo(5000L);
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo("[68,1,111,{\"caller\":17218,\"timeout\":5000},[\"Hello world\"]]");

		invocationMessage = new InvocationMessage(1, 111, 17218L, "com.myapp.orders.create", "caller1", "admin", 9,
				true, null, Arrays.asList("Hello world"), null);
		assertThat(invocationMessage.isReceiveProgress()).isTrue();
		assertThat(invocationMessage.getCallerTrustLevel()).isEqualTo(9);
		json = serializeToJson(invocationMessage);
		assertThat(json).isEqualTo(
				"[68,1,111,{\"caller\":17218,\"procedure\":\"com.myapp.orders.create\",\"caller_authid\":\"caller1\",\"caller_authrole\":\"admin\",\"caller_trustlevel\":9,\"receive_progress\":true},[\"Hello world\"]]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[68, 6131533, 9823526, {}]";

		InvocationMessage invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823526L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).isNull();
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823527, {}, [\"Hello, world!\"]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823527L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("Hello, world!");
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823528, {}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823529, {}, [\"johnny\"], {\"firstname\": \"John\",\"surname\": \"Doe\"}]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823529L);
		assertThat(invocationMessage.getCaller()).isNull();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly("johnny");
		assertThat(invocationMessage.getArgumentsKw()).containsExactly(MapEntry.entry("firstname", "John"),
				MapEntry.entry("surname", "Doe"));

		json = "[68, 6131533, 9823528, {\"caller\": 3335656}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isEqualTo(3335656);
		assertThat(invocationMessage.getProcedure()).isNull();
		assertThat(invocationMessage.getCallerAuthId()).isNull();
		assertThat(invocationMessage.getCallerAuthRole()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getTimeout()).isNull();
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823528, {\"caller\": 3335656, \"caller_authid\": \"caller1\", \"caller_authrole\": \"admin\"}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isEqualTo(3335656);
		assertThat(invocationMessage.getProcedure()).isNull();
		assertThat(invocationMessage.getCallerAuthId()).isEqualTo("caller1");
		assertThat(invocationMessage.getCallerAuthRole()).isEqualTo("admin");
		assertThat(invocationMessage.getCallerTrustLevel()).isNull();
		assertThat(invocationMessage.isReceiveProgress()).isFalse();
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823528, {\"caller\": 3335656, \"caller_trustlevel\": 5}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCallerTrustLevel()).isEqualTo(5);

		json = "[68, 6131533, 9823528, {\"caller\": 3335656, \"procedure\": \"com.myapp.orders.create\"}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getProcedure()).isEqualTo("com.myapp.orders.create");

		json = "[68, 6131533, 9823528, {\"caller\": 3335656, \"timeout\": 5000}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.getCode()).isEqualTo(68);
		assertThat(invocationMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(invocationMessage.getRegistrationId()).isEqualTo(9823528L);
		assertThat(invocationMessage.getCaller()).isEqualTo(3335656);
		assertThat(invocationMessage.getTimeout()).isEqualTo(5000L);
		assertThat(invocationMessage.getArguments()).containsExactly(23, 7);
		assertThat(invocationMessage.getArgumentsKw()).isNull();

		json = "[68, 6131533, 9823528, {\"caller\": 3335656, \"receive_progress\": true}, [23, 7]]";
		invocationMessage = deserializeInvocationMessage(json);
		assertThat(invocationMessage.isReceiveProgress()).isTrue();
	}

	private InvocationMessage deserializeInvocationMessage(String json) throws IOException {
		return Objects.requireNonNull(WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8)));
	}

}
