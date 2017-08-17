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

public class UnsubscribedMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		UnsubscribedMessage unsubscribedMessage = new UnsubscribedMessage(1);

		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(1);

		String json = serializeToJson(unsubscribedMessage);
		assertThat(json).isEqualTo("[35,1]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[35, 85346237]";

		UnsubscribedMessage unsubscribedMessage = WampMessage
				.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));

		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(85346237L);
	}

}
