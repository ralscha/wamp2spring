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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class UnregisterMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		UnregisterMessage unregisterMessage = new UnregisterMessage(14, 23);

		assertThat(unregisterMessage.getCode()).isEqualTo(66);
		assertThat(unregisterMessage.getRequestId()).isEqualTo(14);
		assertThat(unregisterMessage.getRegistrationId()).isEqualTo(23);

		String json = serializeToJson(unregisterMessage);
		assertThat(json).isEqualTo("[66,14,23]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[66, 788923562, 2103333224]";

		UnregisterMessage unregisterMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(unregisterMessage.getCode()).isEqualTo(66);
		assertThat(unregisterMessage.getRequestId()).isEqualTo(788923562L);
		assertThat(unregisterMessage.getRegistrationId()).isEqualTo(2103333224L);
	}

}
