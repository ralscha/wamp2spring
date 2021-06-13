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

import org.junit.jupiter.api.Test;

public class UnsubscribedMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		UnsubscribedMessage unsubscribedMessage = new UnsubscribedMessage(1);
		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(1);
		assertThat(unsubscribedMessage.getSubscriptionId()).isNull();
		assertThat(unsubscribedMessage.getReason()).isNull();
		String json = serializeToJson(unsubscribedMessage);
		assertThat(json).isEqualTo("[35,1]");

		unsubscribedMessage = new UnsubscribedMessage(2, 1208L, "removed");
		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(2);
		assertThat(unsubscribedMessage.getSubscriptionId()).isEqualTo(1208L);
		assertThat(unsubscribedMessage.getReason()).isEqualTo("removed");
		json = serializeToJson(unsubscribedMessage);
		assertThat(json)
				.isEqualTo("[35,2,{\"reason\":\"removed\",\"subscription\":1208}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[35, 85346237]";
		UnsubscribedMessage unsubscribedMessage = WampMessage
				.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(85346237L);
		assertThat(unsubscribedMessage.getSubscriptionId()).isNull();
		assertThat(unsubscribedMessage.getReason()).isNull();

		json = "[35, 0, {\"subscription\":1213,\"reason\":\"the reason\"}]";
		unsubscribedMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(unsubscribedMessage.getCode()).isEqualTo(35);
		assertThat(unsubscribedMessage.getRequestId()).isEqualTo(0);
		assertThat(unsubscribedMessage.getSubscriptionId()).isEqualTo(1213L);
		assertThat(unsubscribedMessage.getReason()).isEqualTo("the reason");
	}

}
