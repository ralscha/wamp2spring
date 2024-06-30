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
package ch.rasc.wamp2spring.pubsub;

import java.util.Map;

public class SubscriptionDetail {

	private final long id;
	private final long createdTimeMillis;
	private final String topic;
	private final MatchPolicy matchPolicy;
	private final Map<String, Object> options;

	public SubscriptionDetail(Subscription subscription) {
		this.id = subscription.getSubscriptionId();
		this.createdTimeMillis = subscription.getCreatedTimeMillis();
		this.topic = subscription.getTopic();
		this.matchPolicy = subscription.getMatchPolicy();
		this.options = subscription.getOptions();
	}

	public long getId() {
		return this.id;
	}

	public long getCreatedTimeMillis() {
		return this.createdTimeMillis;
	}

	public String getTopic() {
		return this.topic;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public Map<String, Object> getOptions() {
		return this.options;
	}

	@Override
	public String toString() {
		return "SubscriptionDetail [id=" + this.id + ", createdTimeMillis="
				+ this.createdTimeMillis + ", topic=" + this.topic + ", matchPolicy="
				+ this.matchPolicy + ", options=" + this.options + "]";
	}

}
