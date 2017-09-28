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

package ch.rasc.wamp2spring.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Collection helper class
 */
public class CollectionHelper {

	/**
	 * Converts a vararg argument to a {@link List}.
	 *
	 * @param arguments variable number of instances
	 * @return a {@link List} instance with the provided arguments
	 */
	@SafeVarargs
	@Nullable
	public static <T> List<T> toList(@Nullable T... arguments) {
		if (arguments != null) {
			if (arguments.length == 1) {
				return Collections.singletonList(arguments[0]);
			}
			if (arguments.length > 1) {
				return Arrays.asList(arguments);
			}
		}

		return null;
	}

	/**
	 * Converts an arbitrary {@link Collection} to a {@link List}.
	 * <p>
	 * Returns the same object if the provided parameter is already an instance of
	 * {@link List}.
	 *
	 * @param collection an arbitrary instance of {@link Collection}
	 * @return an instance of {@link List} containing all the elements of the provided
	 * collection
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public static <T> List<Object> toList(@Nullable Collection<T> collection) {
		if (collection != null) {
			if (collection instanceof List) {
				return (List<Object>) collection;
			}
			return new ArrayList<>(collection);
		}
		return null;
	}

	/**
	 * Converts an arbitrary {@link Collection} to a {@link Set}. Removes duplicates.
	 *
	 * @param collection an arbitrary instance of {@link Collection}
	 * @return an instance of {@link Set} containing all the elements of the provided
	 * collection.
	 */
	@Nullable
	public static Set<Long> toSet(@Nullable Collection<Long> collection) {
		if (collection != null) {
			if (collection instanceof Set) {
				return (Set<Long>) collection;
			}
			return new HashSet<>(collection);
		}
		return null;
	}
}
