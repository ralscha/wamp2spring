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

import org.junit.Test;

public class UnregisteredMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		UnregisteredMessage unregisteredMessage = new UnregisteredMessage(13);

		assertThat(unregisteredMessage.getCode()).isEqualTo(67);
		assertThat(unregisteredMessage.getRequestId()).isEqualTo(13);

		String json = serializeToJson(unregisteredMessage);
		assertThat(json).isEqualTo("[67,13]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[67, 788923562]";

		UnregisteredMessage unregisteredMessage = WampMessage
				.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));

		assertThat(unregisteredMessage.getCode()).isEqualTo(67);
		assertThat(unregisteredMessage.getRequestId()).isEqualTo(788923562L);
	}

}
