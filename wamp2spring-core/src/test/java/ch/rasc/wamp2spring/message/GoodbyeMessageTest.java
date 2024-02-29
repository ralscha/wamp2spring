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

import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.WampError;

public class GoodbyeMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		GoodbyeMessage goodbyeMessage = new GoodbyeMessage(WampError.GOODBYE_AND_OUT,
				"theMessage");

		assertThat(goodbyeMessage.getCode()).isEqualTo(6);
		assertThat(goodbyeMessage.getMessage()).isEqualTo("theMessage");
		assertThat(goodbyeMessage.getReason())
				.isEqualTo(WampError.GOODBYE_AND_OUT.getExternalValue());

		String json = serializeToJson(goodbyeMessage);
		assertThat(json).isEqualTo(
				"[6,{\"message\":\"theMessage\"},\"wamp.error.goodbye_and_out\"]");

		goodbyeMessage = new GoodbyeMessage(WampError.NO_SUCH_PROCEDURE);
		json = serializeToJson(goodbyeMessage);
		assertThat(json).isEqualTo("[6,{},\"wamp.error.no_such_procedure\"]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[6, {\"message\": \"The host is shutting down now.\"},\"wamp.error.system_shutdown\"]";

		GoodbyeMessage goodbyeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(goodbyeMessage.getCode()).isEqualTo(6);
		assertThat(goodbyeMessage.getMessage())
				.isEqualTo("The host is shutting down now.");
		assertThat(goodbyeMessage.getReason()).isEqualTo("wamp.error.system_shutdown");

		json = "[6,{},\"wamp.error.no_such_procedure\"]";
		goodbyeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(goodbyeMessage.getCode()).isEqualTo(6);
		assertThat(goodbyeMessage.getMessage()).isNull();
		assertThat(goodbyeMessage.getReason()).isEqualTo("wamp.error.no_such_procedure");

	}

}
