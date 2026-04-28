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

public class ChallengeMessageTest extends BaseMessageTest {

	@Test
	public void serializeDeserializeTest() throws IOException {
		ChallengeMessage challengeMessage = new ChallengeMessage("ticket",
				Map.of("challenge", "Please provide ticket"));

		assertThat(challengeMessage.getCode()).isEqualTo(4);
		assertThat(challengeMessage.getAuthMethod()).isEqualTo("ticket");

		String json = serializeToJson(challengeMessage);
		assertThat(json).isEqualTo("[4,\"ticket\",{\"challenge\":\"Please provide ticket\"}]");

		ChallengeMessage deserialized = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(deserialized.getAuthMethod()).isEqualTo("ticket");
		assertThat(deserialized.getExtra()).containsEntry("challenge", "Please provide ticket");
	}

}