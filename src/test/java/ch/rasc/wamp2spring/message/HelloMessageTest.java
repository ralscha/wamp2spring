/**
 * Copyright 2017-2017 Ralph Schaer <ralphschaer@gmail.com>
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
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class HelloMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		List<WampRole> roles = createRoles();
		HelloMessage helloMessage = new HelloMessage("aRealm", roles);

		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRealm()).isEqualTo("aRealm");
		assertThat(helloMessage.getRoles()).isEqualTo(roles);

		String json = serializeToJson(helloMessage);
		assertThat(json).isEqualTo(
				"[1,\"aRealm\",{\"roles\":{\"publisher\":{\"features\":{\"publisher_exclusion\":true}},\"subscriber\":{\"features\":{\"subscriber_blackwhite_listing\":true}}}}]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[1, \"somerealm\", { \"roles\": { \"publisher\": {}, \"subscriber\": {} } }]";
		HelloMessage helloMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRealm()).isEqualTo("somerealm");
		assertThat(helloMessage.getRoles()).containsOnly(new WampRole("publisher"),
				new WampRole("subscriber"));

		json = "[1,\"aRealm\",{\"roles\":{\"publisher\":{\"features\":{\"publisher_exclusion\":true}},\"subscriber\":{\"features\":{\"subscriber_blackwhite_listing\":true}}}}]";
		helloMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(helloMessage.getCode()).isEqualTo(1);
		assertThat(helloMessage.getRealm()).isEqualTo("aRealm");
		assertThat(helloMessage.getRoles()).containsOnlyElementsOf(createRoles());
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
