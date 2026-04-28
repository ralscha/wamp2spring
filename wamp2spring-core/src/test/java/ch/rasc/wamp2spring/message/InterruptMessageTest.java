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

public class InterruptMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		InterruptMessage interruptMessage = new InterruptMessage(6131533L, null);
		assertThat(interruptMessage.getCode()).isEqualTo(69);
		assertThat(interruptMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(interruptMessage.getMode()).isNull();
		String json = serializeToJson(interruptMessage);
		assertThat(json).isEqualTo("[69,6131533,{}]");

		interruptMessage = new InterruptMessage(6131533L, CancelMessage.MODE_KILLNOWAIT);
		assertThat(interruptMessage.getCode()).isEqualTo(69);
		assertThat(interruptMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
		json = serializeToJson(interruptMessage);
		assertThat(json).isEqualTo("[69,6131533,{\"mode\":\"killnowait\"}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[69, 6131533, {}]";

		InterruptMessage interruptMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(interruptMessage.getCode()).isEqualTo(69);
		assertThat(interruptMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(interruptMessage.getMode()).isNull();

		json = "[69, 6131533, {\"mode\": \"killnowait\"}]";
		interruptMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(interruptMessage.getCode()).isEqualTo(69);
		assertThat(interruptMessage.getRequestId()).isEqualTo(6131533L);
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
	}

}