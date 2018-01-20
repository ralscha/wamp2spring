/**
 * Copyright 2017-2018 the original author or authors.
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
package ch.rasc.wamp2spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class CollectionHelperTest {

	@Test
	public void testToListTArray() {
		assertThat(CollectionHelper.toList()).isNull();
		assertThat(CollectionHelper.toList(1)).isInstanceOf(List.class)
				.containsExactly(1);
		assertThat(CollectionHelper.toList(1, 2, 3)).isInstanceOf(List.class)
				.containsExactly(1, 2, 3);
	}

	@Test
	public void testToListCollectionOfT() {
		assertThat(CollectionHelper.toList((List<String>) null)).isNull();
		assertThat(CollectionHelper.toList(Arrays.asList(1))).isInstanceOf(List.class)
				.containsExactly(1);
		assertThat(CollectionHelper.toList(Arrays.asList(1, 2, 3)))
				.isInstanceOf(List.class).containsExactly(1, 2, 3);

		Set<Long> set = new HashSet<>();
		set.add(11L);
		set.add(22L);
		assertThat(CollectionHelper.toList(set)).isInstanceOf(List.class)
				.containsOnly(11L, 22L);
	}

	@Test
	public void testToSet() {
		assertThat(CollectionHelper.toSet((Set<Long>) null)).isNull();
		Set<Long> set = new HashSet<>();
		set.add(33L);
		set.add(44L);
		assertThat(CollectionHelper.toSet(set)).isInstanceOf(Set.class).containsOnly(33L,
				44L);
		assertThat(CollectionHelper.toSet(Arrays.asList(1L))).isInstanceOf(Set.class)
				.containsExactly(1L);
		assertThat(CollectionHelper.toSet(Arrays.asList(1L, 2L, 3L)))
				.isInstanceOf(Set.class).containsExactly(1L, 2L, 3L);
	}

}
