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
package ch.rasc.wamp2spring.rpc;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class SessionRegistryTest {

	@Test
	public void tracksSessionsAndOptionalAuthRoleFilter() {
		SessionRegistry registry = new SessionRegistry();
		registry.add(new SessionDetail(101L, "ws-1", "anna", "admin", 1L));
		registry.add(new SessionDetail(202L, "ws-2", "bob", "user", 2L));
		registry.add(new SessionDetail(303L, "ws-3", null, null, 3L));

		assertThat(registry.count(null)).isEqualTo(3);
		assertThat(registry.list(null)).containsExactly(101L, 202L, 303L);
		assertThat(registry.count(List.of("admin"))).isEqualTo(1);
		assertThat(registry.list(List.of("user", "admin"))).containsExactly(101L, 202L);
		assertThat(registry.get(202L)).extracting(SessionDetail::getAuthId).isEqualTo("bob");

		SessionDetail removed = registry.remove(202L);
		assertThat(removed).isNotNull();
		SessionDetail requiredRemoved = Objects.requireNonNull(removed);
		assertThat(requiredRemoved.getSessionId()).isEqualTo(202L);
		assertThat(registry.list(null)).containsExactly(101L, 303L);
	}

	@Test
	public void filtersSessionsByRealm() {
		SessionRegistry registry = new SessionRegistry();
		registry.add(new SessionDetail(101L, "ws-1", "realm-one", "anna", "admin", "ticket", "static", 1L));
		registry.add(new SessionDetail(202L, "ws-2", "realm-two", "bob", "user", "ticket", "static", 2L));
		registry.add(new SessionDetail(303L, "ws-3", "realm-one", "carl", "user", "ticket", "static", 3L));

		assertThat(registry.count("realm-one", null)).isEqualTo(2);
		assertThat(registry.list("realm-one", null)).containsExactly(101L, 303L);
		assertThat(registry.list("realm-two", List.of("user"))).containsExactly(202L);
		assertThat(registry.findByAuthId("realm-one", "bob")).isEmpty();
		assertThat(registry.findByAuthRole("realm-one", "user")).extracting(SessionDetail::getSessionId)
			.containsExactly(303L);
	}

}