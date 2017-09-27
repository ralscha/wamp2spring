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

package ch.rasc.wamp2spring.testsupport;

import java.util.HashMap;
import java.util.Map;

public class Maps {

	public static MapVrapper map(String k, Object v) {
		return new MapVrapper(k, v);
	}

	public static final class MapVrapper {
		private final HashMap<String, Object> map;

		public MapVrapper(String k, Object v) {
			this.map = new HashMap<>();
			this.map.put(k, v);
		}

		public MapVrapper map(String k, Object v) {
			this.map.put(k, v);
			return this;
		}

		public Map<String, Object> getMap() {
			return this.map;
		}
	}

}