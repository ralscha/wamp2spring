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

public class CancelMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		CancelMessage cancelMessage = new CancelMessage(7814135L);
		assertThat(cancelMessage.getCode()).isEqualTo(49);
		assertThat(cancelMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(cancelMessage.getMode()).isNull();
		assertThat(cancelMessage.getModeOrDefault()).isEqualTo(CancelMessage.MODE_SKIP);
		String json = serializeToJson(cancelMessage);
		assertThat(json).isEqualTo("[49,7814135,{}]");

		cancelMessage = new CancelMessage(7814135L, CancelMessage.MODE_KILLNOWAIT);
		assertThat(cancelMessage.getCode()).isEqualTo(49);
		assertThat(cancelMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(cancelMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
		json = serializeToJson(cancelMessage);
		assertThat(json).isEqualTo("[49,7814135,{\"mode\":\"killnowait\"}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[49, 7814135, {}]";

		CancelMessage cancelMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(cancelMessage.getCode()).isEqualTo(49);
		assertThat(cancelMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(cancelMessage.getMode()).isNull();
		assertThat(cancelMessage.getModeOrDefault()).isEqualTo(CancelMessage.MODE_SKIP);

		json = "[49, 7814135, {\"mode\": \"killnowait\"}]";
		cancelMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(cancelMessage.getCode()).isEqualTo(49);
		assertThat(cancelMessage.getRequestId()).isEqualTo(7814135L);
		assertThat(cancelMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
	}

}