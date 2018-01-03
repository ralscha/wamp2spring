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
package ch.rasc.wamp2spring.config;

import org.springframework.lang.Nullable;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

/**
 * Matches a topic or a procedure
 */
public class DestinationMatch {
	private final String destination;

	private final MatchPolicy matchPolicy;

	@Nullable
	private final String wildcardComponents[];

	public DestinationMatch(String destination) {
		this(destination, MatchPolicy.EXACT);
	}

	public DestinationMatch(String destination, MatchPolicy matchPolicy) {
		this.destination = destination;
		this.matchPolicy = matchPolicy;

		if (matchPolicy == MatchPolicy.WILDCARD) {
			this.wildcardComponents = destination.split("\\.");
		}
		else {
			this.wildcardComponents = null;
		}
	}

	public String getDestination() {
		return this.destination;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	/**
	 * Checks if a destination matches with this destination
	 *
	 * @param queryDestination the destination
	 * @return true if the destination matches
	 */
	public boolean matches(String queryDestination) {
		if (this.matchPolicy == MatchPolicy.EXACT) {
			return this.destination.equals(queryDestination);
		}

		if (this.matchPolicy == MatchPolicy.PREFIX) {
			return queryDestination.startsWith(this.destination);
		}

		String[] components = queryDestination.split("\\.");
		return matchesWildcard(components);
	}

	/**
	 * Checks if a destination matches with this destination. Used for
	 * MatchPolicy.WILDCARD destinations.
	 *
	 * @param components the destination splitted in a String array
	 * @return true if the destination matches
	 */
	public boolean matchesWildcard(String[] components) {
		if (this.wildcardComponents != null
				&& components.length == this.wildcardComponents.length) {
			for (int i = 0; i < components.length; i++) {
				String wc = this.wildcardComponents[i];
				if (wc.length() > 0 && !components[i].equals(wc)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

}
