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
package ch.rasc.wampspring.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WelcomeMessageTest extends BaseMessageTest {

	@SuppressWarnings({ "unchecked" })
	@Test
	public void serializeTest()
			throws JsonParseException, JsonMappingException, IOException {
		List<WampRole> roles = createRoles();

		WelcomeMessage welcomeMessage = new WelcomeMessage(9129137332L, roles, "realm");

		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).isEqualTo(roles);

		String json = serializeToJson(welcomeMessage);
		String expected = "[2,9129137332,{\"roles\":{\"dealer\":{\"features\":{\"caller_identification\":true}},\"broker\":{\"features\":{\"subscriber_blackwhite_listing\":true,\"publisher_exclusion\":true,\"publisher_identification\":true,\"pattern_based_subscription\":true}}},\"realm\":\"realm\"}]";
		ObjectMapper om = new ObjectMapper();
		assertThat(om.readValue(json, List.class))
				.isEqualTo(om.readValue(expected, List.class));
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[ 2 , 9129137332 , { \"roles\" : { \"broker\" : {} }}]";

		WelcomeMessage welcomeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).containsExactly(new WampRole("broker"));

		json = "[2,9129137332,{\"roles\":{\"dealer\":{\"features\":{\"caller_identification\":true}},\"broker\":{\"features\":{\"subscriber_blackwhite_listing\":true,\"publisher_exclusion\":true,\"publisher_identification\":true,\"pattern_based_subscription\":true}}},\"realm\":\"realm\"}]";
		welcomeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).containsOnlyElementsOf(createRoles());
	}

	private static List<WampRole> createRoles() {
		List<WampRole> roles = new ArrayList<>();

		WampRole dealer = new WampRole("dealer");
		dealer.addFeature("caller_identification");
		roles.add(dealer);

		WampRole broker = new WampRole("broker");
		broker.addFeature("subscriber_blackwhite_listing");
		broker.addFeature("publisher_exclusion");
		broker.addFeature("publisher_identification");
		broker.addFeature("pattern_based_subscription");
		roles.add(broker);

		return roles;
	}
}
