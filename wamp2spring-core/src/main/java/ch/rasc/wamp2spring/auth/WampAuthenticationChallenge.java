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
package ch.rasc.wamp2spring.auth;

import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public final class WampAuthenticationChallenge {

	private final Map<String, Object> extra;

	@Nullable private final Object state;

	public WampAuthenticationChallenge(@Nullable Map<String, Object> extra) {
		this(extra, null);
	}

	public WampAuthenticationChallenge(@Nullable Map<String, Object> extra, @Nullable Object state) {
		this.extra = extra != null ? Collections.unmodifiableMap(extra) : Map.of();
		this.state = state;
	}

	public Map<String, Object> getExtra() {
		return this.extra;
	}

	@Nullable public Object getState() {
		return this.state;
	}

}