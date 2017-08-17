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
package ch.rasc.wamp2spring.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

public class CollectionHelper {

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

	@SuppressWarnings("unchecked")
	@Nullable
	public static <T> List<Object> toList(@Nullable Collection<T> result) {
		if (result != null) {
			if (result instanceof List) {
				return (List<Object>) result;
			}
			return new ArrayList<>(result);
		}
		return null;
	}
}
