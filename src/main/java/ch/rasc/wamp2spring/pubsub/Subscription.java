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
package ch.rasc.wamp2spring.pubsub;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import ch.rasc.wamp2spring.config.TopicMatch;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

class Subscription {
	private final TopicMatch topicMatch;

	private final long subscriptionId;

	private final Set<Subscriber> subscribers;

	private final long createdTimeMillis;

	@Nullable
	private List<InvocableHandlerMethod> eventListenerHandlerMethods = null;

	Subscription(String topic, MatchPolicy matchPolicy, long subscriptionId) {
		this.createdTimeMillis = System.currentTimeMillis();

		this.topicMatch = new TopicMatch(topic, matchPolicy);

		this.subscriptionId = subscriptionId;
		this.subscribers = ConcurrentHashMap.newKeySet();
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
		return this.topicMatch.getTopic();
	}

	MatchPolicy getMatchPolicy() {
		return this.topicMatch.getMatchPolicy();
	}

	public TopicMatch getTopicMatch() {
		return this.topicMatch;
	}

	long getSubscriptionId() {
		return this.subscriptionId;
	}

	Set<Subscriber> getSubscribers() {
		return this.subscribers;
	}

	@Nullable
	List<InvocableHandlerMethod> getEventListenerHandlerMethods() {
		return this.eventListenerHandlerMethods;
	}

}
