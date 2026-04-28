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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class AuthenticateMessageTest extends BaseMessageTest {

	@Test
	public void serializeDeserializeTest() throws IOException {
		AuthenticateMessage authenticateMessage = new AuthenticateMessage("demo-token",
				Map.of("channel_binding", "none"));

		assertThat(authenticateMessage.getCode()).isEqualTo(5);
		assertThat(authenticateMessage.getSignature()).isEqualTo("demo-token");

		String json = serializeToJson(authenticateMessage);
		assertThat(json).isEqualTo("[5,\"demo-token\",{\"channel_binding\":\"none\"}]");

		AuthenticateMessage deserialized = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(deserialized.getSignature()).isEqualTo("demo-token");
		assertThat(deserialized.getExtra()).containsEntry("channel_binding", "none");
	}

}