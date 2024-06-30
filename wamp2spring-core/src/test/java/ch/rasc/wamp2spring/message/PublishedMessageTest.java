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

public class PublishedMessageTest extends BaseMessageTest {
	@Test
	public void serializeTest() {
		PublishedMessage publishedMessage = new PublishedMessage(44, 121);

		assertThat(publishedMessage.getCode()).isEqualTo(17);
		assertThat(publishedMessage.getRequestId()).isEqualTo(44);
		assertThat(publishedMessage.getPublicationId()).isEqualTo(121);

		String json = serializeToJson(publishedMessage);
		assertThat(json).isEqualTo("[17,44,121]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[17, 239714735, 4429313566]";

		PublishedMessage publishedMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(publishedMessage.getCode()).isEqualTo(17);
		assertThat(publishedMessage.getRequestId()).isEqualTo(239714735L);
		assertThat(publishedMessage.getPublicationId()).isEqualTo(4429313566L);
	}
}
