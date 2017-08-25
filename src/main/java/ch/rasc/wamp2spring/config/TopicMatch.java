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
package ch.rasc.wamp2spring.config;

import javax.annotation.Nullable;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class TopicMatch {
	private final String topic;

	private final MatchPolicy matchPolicy;

	@Nullable
	private final String wildcardComponents[];

	public TopicMatch(String topic, MatchPolicy matchPolicy) {
		this.topic = topic;
		this.matchPolicy = matchPolicy;

		if (matchPolicy == MatchPolicy.WILDCARD) {
			this.wildcardComponents = topic.split("\\.");
		}
		else {
			this.wildcardComponents = null;
		}
	}

	public String getTopic() {
		return this.topic;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public boolean matches(String queryTopic) {
		if (this.matchPolicy == MatchPolicy.EXACT) {
			return this.topic.equals(queryTopic);
		}

		if (this.matchPolicy == MatchPolicy.PREFIX) {
			return queryTopic.startsWith(this.topic);
		}

		String[] components = queryTopic.split("\\.");
		return matchesWildcard(components);
	}

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
