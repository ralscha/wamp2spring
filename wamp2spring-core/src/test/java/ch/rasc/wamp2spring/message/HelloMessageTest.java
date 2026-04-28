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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class HelloMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		List<WampRole> roles = createRoles();
		HelloMessage helloMessage = new HelloMessage(roles, List.of("ticket"), "alice", Map.of("ticket", "demo-token"));

		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRoles()).isEqualTo(roles);
		assertThat(helloMessage.getAuthMethods()).containsExactly("ticket");
		assertThat(helloMessage.getAuthId()).isEqualTo("alice");

		String json = serializeToJson(helloMessage);
		assertThat(json).isEqualTo(
				"[1,{\"roles\":{\"publisher\":{\"features\":{\"publisher_exclusion\":true}},\"subscriber\":{\"features\":{\"subscriber_blackwhite_listing\":true}}},\"authmethods\":[\"ticket\"],\"authid\":\"alice\",\"authextra\":{\"ticket\":\"demo-token\"}}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[1, { \"roles\": { \"publisher\": {}, \"subscriber\": {} } }]";
		HelloMessage helloMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRoles()).containsOnly(new WampRole("publisher"), new WampRole("subscriber"));
		assertThat(helloMessage.getAuthMethods()).isEmpty();
		assertThat(helloMessage.getAuthId()).isNull();

		json = "[1,{\"roles\":{\"publisher\":{\"features\":{\"publisher_exclusion\":true}},\"subscriber\":{\"features\":{\"subscriber_blackwhite_listing\":true}}},\"authmethods\":[\"ticket\"],\"authid\":\"alice\",\"authextra\":{\"ticket\":\"demo-token\"}}]";
		helloMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRoles()).containsExactlyInAnyOrderElementsOf(createRoles());
		assertThat(helloMessage.getAuthMethods()).containsExactly("ticket");
		assertThat(helloMessage.getAuthId()).isEqualTo("alice");
		assertThat(helloMessage.getAuthExtra()).containsEntry("ticket", "demo-token");
	}

	private static List<WampRole> createRoles() {
		List<WampRole> roles = new ArrayList<>();
		WampRole publisher = new WampRole("publisher");
		publisher.addFeature("publisher_exclusion");
		roles.add(publisher);

		WampRole subscriber = new WampRole("subscriber");
		subscriber.addFeature("subscriber_blackwhite_listing");
		roles.add(subscriber);

		return roles;
	}

}
