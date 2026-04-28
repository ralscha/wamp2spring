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

import tools.jackson.databind.ObjectMapper;

public class WelcomeMessageTest extends BaseMessageTest {

	@SuppressWarnings({ "unchecked" })
	@Test
	public void serializeTest() throws IOException {
		List<WampRole> roles = createRoles();

		WelcomeMessage welcomeMessage = new WelcomeMessage(9129137332L, roles, "realm", "alice", "admin", "ticket",
				"static", Map.of("tenant", "demo"));

		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).isEqualTo(roles);
		assertThat(welcomeMessage.getAuthMethod()).isEqualTo("ticket");

		String json = serializeToJson(welcomeMessage);
		String expected = "[2,9129137332,{\"roles\":{\"dealer\":{\"features\":{\"progressive_call_results\":true,\"progressive_call_invocations\":true,\"caller_identification\":true,\"pattern_based_registration\":true,\"shared_registration\":true,\"sharded_registration\":true,\"registration_meta_api\":true,\"session_meta_api\":true,\"registration_revocation\":true}},\"broker\":{\"features\":{\"subscriber_blackwhite_listing\":true,\"publisher_exclusion\":true,\"publisher_identification\":true,\"pattern_based_subscription\":true,\"sharded_subscription\":true,\"event_history\":true,\"session_meta_api\":true,\"subscription_meta_api\":true,\"subscription_revocation\":true}}},\"realm\":\"realm\",\"authid\":\"alice\",\"authrole\":\"admin\",\"authmethod\":\"ticket\",\"authprovider\":\"static\",\"authextra\":{\"tenant\":\"demo\"}}]";
		ObjectMapper om = new ObjectMapper();
		assertThat(om.readValue(json, List.class)).isEqualTo(om.readValue(expected, List.class));
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[ 2 , 9129137332 , { \"roles\" : { \"broker\" : {} }}]";

		WelcomeMessage welcomeMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));
		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).containsExactly(new WampRole("broker"));
		assertThat(welcomeMessage.getAuthMethod()).isEqualTo("anonymous");

		json = "[2,9129137332,{\"roles\":{\"dealer\":{\"features\":{\"progressive_call_results\":true,\"progressive_call_invocations\":true,\"caller_identification\":true,\"pattern_based_registration\":true,\"shared_registration\":true,\"sharded_registration\":true,\"registration_meta_api\":true,\"session_meta_api\":true,\"registration_revocation\":true}},\"broker\":{\"features\":{\"subscriber_blackwhite_listing\":true,\"publisher_exclusion\":true,\"publisher_identification\":true,\"pattern_based_subscription\":true,\"sharded_subscription\":true,\"event_history\":true,\"session_meta_api\":true,\"subscription_meta_api\":true,\"subscription_revocation\":true}}},\"realm\":\"realm\",\"authid\":\"alice\",\"authrole\":\"admin\",\"authmethod\":\"ticket\",\"authprovider\":\"static\",\"authextra\":{\"tenant\":\"demo\"}}]";
		welcomeMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(welcomeMessage.getCode()).isEqualTo(2);
		assertThat(welcomeMessage.getSessionId()).isEqualTo(9129137332L);
		assertThat(welcomeMessage.getRoles()).containsOnlyElementsOf(createRoles());
		assertThat(welcomeMessage.getAuthId()).isEqualTo("alice");
		assertThat(welcomeMessage.getAuthRole()).isEqualTo("admin");
		assertThat(welcomeMessage.getAuthMethod()).isEqualTo("ticket");
		assertThat(welcomeMessage.getAuthProvider()).isEqualTo("static");
		assertThat(welcomeMessage.getAuthExtra()).containsEntry("tenant", "demo");
	}

	private static List<WampRole> createRoles() {
		List<WampRole> roles = new ArrayList<>();

		WampRole dealer = new WampRole("dealer");
		dealer.addFeature("progressive_call_results");
		dealer.addFeature("progressive_call_invocations");
		dealer.addFeature("caller_identification");
		dealer.addFeature("pattern_based_registration");
		dealer.addFeature("shared_registration");
		dealer.addFeature("sharded_registration");
		dealer.addFeature("registration_meta_api");
		dealer.addFeature("session_meta_api");
		dealer.addFeature("registration_revocation");
		roles.add(dealer);

		WampRole broker = new WampRole("broker");
		broker.addFeature("subscriber_blackwhite_listing");
		broker.addFeature("publisher_exclusion");
		broker.addFeature("publisher_identification");
		broker.addFeature("pattern_based_subscription");
		broker.addFeature("sharded_subscription");
		broker.addFeature("event_history");
		broker.addFeature("session_meta_api");
		broker.addFeature("subscription_meta_api");
		broker.addFeature("subscription_revocation");
		roles.add(broker);

		return roles;
	}

}
