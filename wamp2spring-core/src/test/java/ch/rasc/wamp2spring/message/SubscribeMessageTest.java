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

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class SubscribeMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic",
				MatchPolicy.PREFIX, false);

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(1);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.PREFIX);
		assertThat(subscribeMessage.isGetRetained()).isFalse();

		String json = serializeToJson(subscribeMessage);
		assertThat(json).isEqualTo("[32,1,{\"match\":\"prefix\"},\"topic\"]");

		subscribeMessage = new SubscribeMessage(2, "topic2");
		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(subscribeMessage.isGetRetained()).isFalse();

		json = serializeToJson(subscribeMessage);
		assertThat(json).isEqualTo("[32,2,{},\"topic2\"]");

		subscribeMessage = new SubscribeMessage(2, "topic2", true);
		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(subscribeMessage.isGetRetained()).isTrue();

		json = serializeToJson(subscribeMessage);
		assertThat(json).isEqualTo("[32,2,{\"get_retained\":true},\"topic2\"]");

		subscribeMessage = new SubscribeMessage(2, "topic2", MatchPolicy.WILDCARD, true);
		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.WILDCARD);
		assertThat(subscribeMessage.isGetRetained()).isTrue();

		json = serializeToJson(subscribeMessage);
		assertThat(json).isEqualTo(
				"[32,2,{\"match\":\"wildcard\",\"get_retained\":true},\"topic2\"]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[32,1,{\"match\":\"prefix\"},\"topic\"]";

		SubscribeMessage subscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(1);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.PREFIX);
		assertThat(subscribeMessage.isGetRetained()).isFalse();

		json = "[32,2,{},\"topic2\"]";
		subscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(subscribeMessage.isGetRetained()).isFalse();

		json = "[32,2,{\"get_retained\":false},\"topic2\"]";
		subscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(subscribeMessage.isGetRetained()).isFalse();

		json = "[32,2,{\"get_retained\":true},\"topic2\"]";
		subscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(subscribeMessage.isGetRetained()).isTrue();

		json = "[32,2,{\"match\":\"wildcard\",\"get_retained\":true},\"topic2\"]";
		subscribeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(subscribeMessage.getCode()).isEqualTo(32);
		assertThat(subscribeMessage.getRequestId()).isEqualTo(2);
		assertThat(subscribeMessage.getTopic()).isEqualTo("topic2");
		assertThat(subscribeMessage.getMatchPolicy()).isEqualTo(MatchPolicy.WILDCARD);
		assertThat(subscribeMessage.isGetRetained()).isTrue();
	}

}
