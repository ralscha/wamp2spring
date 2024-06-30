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

public class UnsubscribeMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(1, 2);

		assertThat(unsubscribeMessage.getCode()).isEqualTo(34);
		assertThat(unsubscribeMessage.getRequestId()).isEqualTo(1);
		assertThat(unsubscribeMessage.getSubscriptionId()).isEqualTo(2);

		String json = serializeToJson(unsubscribeMessage);
		assertThat(json).isEqualTo("[34,1,2]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[34, 85346237, 5512315355]";

		UnsubscribeMessage unsubscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(unsubscribeMessage.getCode()).isEqualTo(34);
		assertThat(unsubscribeMessage.getRequestId()).isEqualTo(85346237L);
		assertThat(unsubscribeMessage.getSubscriptionId()).isEqualTo(5512315355L);
	}

}
