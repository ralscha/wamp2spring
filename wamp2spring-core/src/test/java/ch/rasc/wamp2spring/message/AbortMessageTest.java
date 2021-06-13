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

import org.junit.Test;

import ch.rasc.wamp2spring.WampError;

public class AbortMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		AbortMessage abortMessage = new AbortMessage(WampError.NO_SUCH_REGISTRATION,
				"No such registration");

		assertThat(abortMessage.getCode()).isEqualTo(3);
		assertThat(abortMessage.getMessage()).isEqualTo("No such registration");
		assertThat(abortMessage.getReason()).isEqualTo("wamp.error.no_such_registration");

		String json = serializeToJson(abortMessage);
		assertThat(json).isEqualTo(
				"[3,{\"message\":\"No such registration\"},\"wamp.error.no_such_registration\"]");

		abortMessage = new AbortMessage(WampError.NO_SUCH_REGISTRATION);

		assertThat(abortMessage.getCode()).isEqualTo(3);
		assertThat(abortMessage.getMessage()).isNull();
		assertThat(abortMessage.getReason()).isEqualTo("wamp.error.no_such_registration");

		json = serializeToJson(abortMessage);
		assertThat(json).isEqualTo("[3,{},\"wamp.error.no_such_registration\"]");

	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[3, {\"message\": \"The realm does not exist.\"}, \"wamp.error.no_such_realm\"]";

		AbortMessage abortMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(abortMessage.getCode()).isEqualTo(3);
		assertThat(abortMessage.getMessage()).isEqualTo("The realm does not exist.");
		assertThat(abortMessage.getReason()).isEqualTo("wamp.error.no_such_realm");

		json = "[3, {}, \"wamp.error.no_such_realm\"]";

		abortMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(abortMessage.getCode()).isEqualTo(3);
		assertThat(abortMessage.getMessage()).isNull();
		assertThat(abortMessage.getReason()).isEqualTo("wamp.error.no_such_realm");
	}

}
