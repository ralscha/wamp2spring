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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.assertj.core.data.MapEntry;
import org.junit.Test;

import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.WampMessage;

public class ErrorMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		ErrorMessage errorMessage = new ErrorMessage(64, 25349185L,
				"wamp.error.procedure_already_exists", null, null);
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(64);
		assertThat(errorMessage.getRequestId()).isEqualTo(25349185L);
		assertThat(errorMessage.getError())
				.isEqualTo("wamp.error.procedure_already_exists");
		assertThat(errorMessage.getArguments()).isNull();
		assertThat(errorMessage.getArgumentsKw()).isNull();
		String json = serializeToJson(errorMessage);
		assertThat(json)
				.isEqualTo("[8,64,25349185,{},\"wamp.error.procedure_already_exists\"]");

		errorMessage = new ErrorMessage(66, 788923562L, "wamp.error.no_such_registration",
				Collections.singletonList("No such registration"), null);
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(66);
		assertThat(errorMessage.getRequestId()).isEqualTo(788923562L);
		assertThat(errorMessage.getError()).isEqualTo("wamp.error.no_such_registration");
		assertThat(errorMessage.getArguments()).containsExactly("No such registration");
		assertThat(errorMessage.getArgumentsKw()).isNull();
		json = serializeToJson(errorMessage);
		assertThat(json).isEqualTo(
				"[8,66,788923562,{},\"wamp.error.no_such_registration\",[\"No such registration\"]]");

		errorMessage = new ErrorMessage(68, 6131533L,
				"com.myapp.error.object_write_protected",
				Collections.singletonList("Object is write protected."),
				Collections.singletonMap("severity", 3));
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(68);
		assertThat(errorMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(errorMessage.getError())
				.isEqualTo("com.myapp.error.object_write_protected");
		assertThat(errorMessage.getArguments())
				.containsExactly("Object is write protected.");
		assertThat(errorMessage.getArgumentsKw())
				.containsExactly(MapEntry.entry("severity", 3));
		json = serializeToJson(errorMessage);
		assertThat(json).isEqualTo(
				"[8,68,6131533,{},\"com.myapp.error.object_write_protected\",[\"Object is write protected.\"],{\"severity\":3}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[8, 64, 25349185, {}, \"wamp.error.procedure_already_exists\"]";

		ErrorMessage errorMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(64);
		assertThat(errorMessage.getRequestId()).isEqualTo(25349185L);
		assertThat(errorMessage.getError())
				.isEqualTo("wamp.error.procedure_already_exists");
		assertThat(errorMessage.getArguments()).isNull();
		assertThat(errorMessage.getArgumentsKw()).isNull();

		json = "[8, 66, 788923562, {}, \"wamp.error.no_such_registration\",[\"No such registration\"]]";
		errorMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(66);
		assertThat(errorMessage.getRequestId()).isEqualTo(788923562L);
		assertThat(errorMessage.getError()).isEqualTo("wamp.error.no_such_registration");
		assertThat(errorMessage.getArguments()).containsExactly("No such registration");
		assertThat(errorMessage.getArgumentsKw()).isNull();

		json = "[8, 68, 6131533, {}, \"com.myapp.error.object_write_protected\", [\"Object is write protected.\"], {\"severity\": 3}]";
		errorMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(errorMessage.getCode()).isEqualTo(8);
		assertThat(errorMessage.getType()).isEqualTo(68);
		assertThat(errorMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(errorMessage.getError())
				.isEqualTo("com.myapp.error.object_write_protected");
		assertThat(errorMessage.getArguments())
				.containsExactly("Object is write protected.");
		assertThat(errorMessage.getArgumentsKw())
				.containsExactly(MapEntry.entry("severity", 3));
	}

}
