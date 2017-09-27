/**
 * Copyright 2017-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.rasc.wamp2spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class DestinationMatchTest {

	@Test
	public void testExactMatch() {
		DestinationMatch dm = new DestinationMatch("dest");
		assertThat(dm.getDestination()).isEqualTo("dest");
		assertThat(dm.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);

		assertThat(dm.matches("dest")).isEqualTo(true);
		assertThat(dm.matchesWildcard(new String[] { "dest" })).isEqualTo(false);
	}

	@Test
	public void testPrefixMatch() {
		DestinationMatch dm = new DestinationMatch("user", MatchPolicy.PREFIX);
		assertThat(dm.getDestination()).isEqualTo("user");
		assertThat(dm.getMatchPolicy()).isEqualTo(MatchPolicy.PREFIX);

		assertThat(dm.matches("user")).isEqualTo(true);
		assertThat(dm.matches("user.create")).isEqualTo(true);
		assertThat(dm.matches("user.update.one")).isEqualTo(true);
		assertThat(dm.matches("usr.delete")).isEqualTo(false);
		assertThat(dm.matchesWildcard(new String[] { "user" })).isEqualTo(false);
	}

	@Test
	public void testWildcardMatch() {
		DestinationMatch dm = new DestinationMatch("crud..update", MatchPolicy.WILDCARD);
		assertThat(dm.getDestination()).isEqualTo("crud..update");
		assertThat(dm.getMatchPolicy()).isEqualTo(MatchPolicy.WILDCARD);

		assertThat(dm.matches("crud.user.update")).isEqualTo(true);
		assertThat(dm.matches("crud.user.delete")).isEqualTo(false);
		assertThat(dm.matches("crud.delete")).isEqualTo(false);
		assertThat(dm.matches("crud.update")).isEqualTo(false);
		assertThat(dm.matchesWildcard(new String[] { "crud", "user", "update" }))
				.isEqualTo(true);
		assertThat(dm.matchesWildcard(new String[] { "crud", "user", "delete" }))
				.isEqualTo(false);
		assertThat(dm.matchesWildcard(new String[] { "crud", "update" }))
				.isEqualTo(false);
	}
}
