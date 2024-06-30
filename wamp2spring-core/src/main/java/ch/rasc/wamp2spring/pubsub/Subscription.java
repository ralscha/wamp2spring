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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

class Subscription {
	private final DestinationMatch topicMatch;

	private final long subscriptionId;

	private final Set<Subscriber> subscribers;

	private final long createdTimeMillis;

	private final Map<String, Object> options;

	@Nullable
	private List<InvocableHandlerMethod> eventListenerHandlerMethods = null;

	Subscription(String topic, MatchPolicy matchPolicy, long subscriptionId,
			Map<String, Object> options) {
		this.createdTimeMillis = System.currentTimeMillis();

		this.topicMatch = new DestinationMatch(topic, matchPolicy);

		this.subscriptionId = subscriptionId;
		this.subscribers = ConcurrentHashMap.newKeySet();
		this.options = options;
	}

	void addEventListenerHandlerMethod(InvocableHandlerMethod handlerMethod) {
		if (this.eventListenerHandlerMethods == null) {
			this.eventListenerHandlerMethods = new ArrayList<>();
		}
		this.eventListenerHandlerMethods.add(handlerMethod);
	}

	void addSubscriber(Subscriber subscriber) {
		this.subscribers.add(subscriber);
	}

	boolean removeSubscriber(Subscriber subscriber) {
		return this.subscribers.remove(subscriber);
	}

	boolean hasSubscribers() {
		return !this.subscribers.isEmpty() || this.eventListenerHandlerMethods != null;
	}

	long getCreatedTimeMillis() {
		return this.createdTimeMillis;
	}

	String getTopic() {
		return this.topicMatch.getDestination();
	}

	MatchPolicy getMatchPolicy() {
		return this.topicMatch.getMatchPolicy();
	}

	public DestinationMatch getTopicMatch() {
		return this.topicMatch;
	}

	long getSubscriptionId() {
		return this.subscriptionId;
	}

	Set<Subscriber> getSubscribers() {
		return this.subscribers;
	}

	public Map<String, Object> getOptions() {
		return this.options;
	}

	@Nullable
	List<InvocableHandlerMethod> getEventListenerHandlerMethods() {
		return this.eventListenerHandlerMethods;
	}

}
