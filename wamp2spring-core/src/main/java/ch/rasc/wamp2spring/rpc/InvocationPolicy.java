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

import org.jspecify.annotations.Nullable;

public enum InvocationPolicy {

	SINGLE("single"), ROUNDROBIN("roundrobin"), RANDOM("random"), FIRST("first"), LAST("last"), SHARDED("sharded");

	private final String externalValue;

	InvocationPolicy(String externalValue) {
		this.externalValue = externalValue;
	}

	public String getExternalValue() {
		return this.externalValue;
	}

	@Nullable public static InvocationPolicy fromExternalValue(String externalValue) {
		for (InvocationPolicy invocationPolicy : values()) {
			if (invocationPolicy.externalValue.equals(externalValue)) {
				return invocationPolicy;
			}
		}
		return null;
	}

}