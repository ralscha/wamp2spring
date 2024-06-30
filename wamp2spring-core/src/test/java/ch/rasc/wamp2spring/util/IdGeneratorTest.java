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
package ch.rasc.wamp2spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

public class IdGeneratorTest {

	@Test
	public void testNewRandomId() {
		assertThat(IdGenerator.newRandomId(null)).isBetween(IdGenerator.MIN,
				IdGenerator.MAX);

		Set<Long> ids = new HashSet<>();
		ids.add(1L);
		assertThat(IdGenerator.newRandomId(ids)).isBetween(IdGenerator.MIN,
				IdGenerator.MAX);
	}

	@Test
	public void testNewLinearId() {
		AtomicLong id = new AtomicLong(0);
		assertThat(IdGenerator.newLinearId(id)).isEqualTo(1);
		assertThat(IdGenerator.newLinearId(id)).isEqualTo(2);

		id.set(IdGenerator.MAX - 1L);
		assertThat(IdGenerator.newLinearId(id)).isEqualTo(IdGenerator.MAX);
		assertThat(IdGenerator.newLinearId(id)).isEqualTo(1L);
	}

}
