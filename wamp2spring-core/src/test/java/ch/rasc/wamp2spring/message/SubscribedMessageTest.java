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

public class SubscribedMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		SubscribedMessage subscribedMessage = new SubscribedMessage(1, 2);

		assertThat(subscribedMessage.getCode()).isEqualTo(33);
		assertThat(subscribedMessage.getRequestId()).isEqualTo(1);
		assertThat(subscribedMessage.getSubscriptionId()).isEqualTo(2);

		String json = serializeToJson(subscribedMessage);
		assertThat(json).isEqualTo("[33,1,2]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[33, 713845233, 5512315355]";

		SubscribedMessage subscribedMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribedMessage.getCode()).isEqualTo(33);
		assertThat(subscribedMessage.getRequestId()).isEqualTo(713845233L);
		assertThat(subscribedMessage.getSubscriptionId()).isEqualTo(5512315355L);
	}

}
