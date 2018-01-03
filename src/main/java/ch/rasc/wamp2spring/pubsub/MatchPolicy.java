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
package ch.rasc.wamp2spring.pubsub;

import org.springframework.lang.Nullable;

public enum MatchPolicy {
	EXACT("exact"), PREFIX("prefix"), WILDCARD("wildcard");

	private final String externalValue;

	private MatchPolicy(String externalValue) {
		this.externalValue = externalValue;
	}

	public String getExternalValue() {
		return this.externalValue;
	}

	@Nullable
	public static MatchPolicy fromExtValue(String externalValue) {
		switch (externalValue) {
		case "exact":
			return EXACT;
		case "prefix":
			return PREFIX;
		case "wildcard":
			return WILDCARD;
		default:
			return null;
		}
	}

}
