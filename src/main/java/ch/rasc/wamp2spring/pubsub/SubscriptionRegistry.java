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

package ch.rasc.wamp2spring.pubsub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * In memory subscription registry
 */
public class SubscriptionRegistry {
	private final static AtomicLong lastSubscriptionId = new AtomicLong(1L);

	private final EnumMap<MatchPolicy, Map<String, Subscription>> subscriptionsByMatch = new EnumMap<>(
			MatchPolicy.class);

	private final Map<Long, Subscription> subscriptionsById = new ConcurrentHashMap<>();

	private final LoadingCache<String, Set<Subscription>> subscriptionsCache = Caffeine
			.newBuilder().maximumSize(512).build(key -> internalFindSubscriptions(key));

	private final Object monitor = new Object();

	public SubscriptionRegistry() {
		this.subscriptionsByMatch.put(MatchPolicy.EXACT,
				new ConcurrentHashMap<String, Subscription>());
		this.subscriptionsByMatch.put(MatchPolicy.PREFIX,
				new ConcurrentHashMap<String, Subscription>());
		this.subscriptionsByMatch.put(MatchPolicy.WILDCARD,
				new ConcurrentHashMap<String, Subscription>());
	}

	SubscribeResult subscribe(SubscribeMessage subscribeMessage) {
		Map<String, Subscription> subscriptionMap = this.subscriptionsByMatch
				.get(subscribeMessage.getMatchPolicy());

		boolean created = false;

		Subscription subscription = subscriptionMap.get(subscribeMessage.getTopic());
		if (subscription == null) {
			synchronized (this.monitor) {
				subscription = subscriptionMap.get(subscribeMessage.getTopic());
				if (subscription == null) {
					long subscriptionId = IdGenerator.newLinearId(lastSubscriptionId);
					subscription = new Subscription(subscribeMessage.getTopic(),
							subscribeMessage.getMatchPolicy(), subscriptionId);
					subscriptionMap.put(subscription.getTopic(), subscription);
					this.subscriptionsById.put(subscriptionId, subscription);
					created = true;
					invalidateCacheEntries(subscription);
				}
			}
		}
		Subscriber subscriber = new Subscriber(subscribeMessage.getWebSocketSessionId(),
				subscribeMessage.getWampSessionId());
		subscription.addSubscriber(subscriber);

		return new SubscribeResult(subscribeMessage.getWampSessionId(), subscription,
				created);
	}

	void subscribeEventHandlers(List<EventListenerInfo> eventListeners) {
		for (EventListenerInfo eventListener : eventListeners) {
			Map<String, Subscription> subscriptionMap = this.subscriptionsByMatch
					.get(eventListener.getMatch());
			for (String topic : eventListener.getTopic()) {
				synchronized (this.monitor) {
					Subscription subscription = subscriptionMap.get(topic);
					if (subscription == null) {
						long subscriptionId = IdGenerator.newLinearId(lastSubscriptionId);
						subscription = new Subscription(topic, eventListener.getMatch(),
								subscriptionId);
						subscriptionMap.put(subscription.getTopic(), subscription);
						this.subscriptionsById.put(subscriptionId, subscription);
						invalidateCacheEntries(subscription);
					}
					subscription.addEventListenerHandlerMethod(
							eventListener.getHandlerMethod());
				}
			}
		}
	}

	UnsubscribeResult unsubscribe(UnsubscribeMessage message) {
		Subscription subscription = this.subscriptionsById
				.get(message.getSubscriptionId());

		if (subscription != null) {
			Subscriber subscriber = new Subscriber(message.getWebSocketSessionId(),
					message.getWampSessionId());

			synchronized (this.monitor) {
				if (subscription.removeSubscriber(subscriber)) {
					boolean deleted = false;
					if (!subscription.hasSubscribers()) {
						this.subscriptionsByMatch.get(subscription.getMatchPolicy())
								.remove(subscription.getTopic());
						this.subscriptionsById.remove(subscription.getSubscriptionId());
						deleted = true;
						invalidateCacheEntries(subscription);
					}
					return new UnsubscribeResult(message.getWampSessionId(), subscription,
							deleted);
				}
			}
		}

		return new UnsubscribeResult(message.getWampSessionId(),
				WampError.NO_SUCH_SUBSCRIPTION);
	}

	List<UnsubscribeResult> removeWebSocketSessionId(String webSocketSessionId,
			long wampSessionId) {
		List<UnsubscribeResult> results = new ArrayList<>();
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			Map<String, Subscription> subscriptionMap = this.subscriptionsByMatch
					.get(matchPolicy);

			for (Subscription subscription : subscriptionMap.values()) {
				Subscriber subscriber = new Subscriber(webSocketSessionId, wampSessionId);

				synchronized (this.monitor) {
					if (subscription.removeSubscriber(subscriber)) {
						boolean deleted = false;
						if (!subscription.hasSubscribers()) {
							subscriptionMap.remove(subscription.getTopic());
							this.subscriptionsById
									.remove(subscription.getSubscriptionId());
							deleted = true;
							invalidateCacheEntries(subscription);
						}

						results.add(new UnsubscribeResult(wampSessionId, subscription,
								deleted));
					}
				}
			}

		}
		return results;
	}

	@Nullable
	Set<Subscription> findSubscriptions(String topic) {
		return this.subscriptionsCache.get(topic);
	}

	private Set<Subscription> internalFindSubscriptions(String topic) {
		Set<Subscription> subscriptions = new HashSet<>();

		Subscription exactSubscription = this.subscriptionsByMatch.get(MatchPolicy.EXACT)
				.get(topic);
		if (exactSubscription != null) {
			subscriptions.add(exactSubscription);
		}

		Map<String, Subscription> prefixSubscriptionMap = this.subscriptionsByMatch
				.get(MatchPolicy.PREFIX);
		for (Subscription prefixSubscription : prefixSubscriptionMap.values()) {
			if (prefixSubscription.getTopicMatch().matches(topic)) {
				subscriptions.add(prefixSubscription);
			}
		}

		Map<String, Subscription> wildcardSubscriptionMap = this.subscriptionsByMatch
				.get(MatchPolicy.WILDCARD);
		String[] components = topic.split("\\.");
		for (Subscription wildcardSubscription : wildcardSubscriptionMap.values()) {
			if (wildcardSubscription.getTopicMatch().matchesWildcard(components)) {
				subscriptions.add(wildcardSubscription);
			}
		}

		return subscriptions;
	}

	private void invalidateCacheEntries(Subscription subscription) {
		if (subscription.getMatchPolicy() == MatchPolicy.EXACT) {
			this.subscriptionsCache.invalidate(subscription.getTopic());
		}
		else {
			DestinationMatch topicMatch = subscription.getTopicMatch();
			this.subscriptionsCache.asMap().keySet().removeIf(topicMatch::matches);
		}
	}

	/**
	 * Returns subscription IDs listed according to matching policies.
	 *
	 * @return subscription IDs grouped by matching policies
	 */
	public EnumMap<MatchPolicy, List<Long>> listSubscriptions() {
		EnumMap<MatchPolicy, List<Long>> result = new EnumMap<>(MatchPolicy.class);

		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			List<Long> subscriptionIds = this.subscriptionsByMatch.get(matchPolicy)
					.values().stream().map(Subscription::getSubscriptionId)
					.collect(Collectors.toList());
			result.put(matchPolicy, subscriptionIds);
		}

		return result;
	}

	/**
	 * Returns the subscription ID (if any) managing a topic, according to the matching
	 * policy.
	 *
	 * @param topic the topic URI
	 * @param matchPolicy the matching policy
	 * @return the subscription id or null if no matching subscription exist
	 */
	@Nullable
	public Long lookupSubscription(String topic, @Nullable MatchPolicy matchPolicy) {
		MatchPolicy me = matchPolicy;
		if (me == null) {
			me = MatchPolicy.EXACT;
		}

		Subscription subscription = this.subscriptionsByMatch.get(me).get(topic);
		if (subscription != null) {
			return subscription.getSubscriptionId();
		}
		return null;
	}

	/**
	 * Returns a list of IDs of subscriptions matching a topic URI, irrespective of match
	 * policy.
	 *
	 * @param topic the topic URI
	 * @return the list of session IDs subscribed to the topic
	 */
	public List<Long> getMatchSubscriptions(String topic) {
		return findSubscriptions(topic).stream().map(Subscription::getSubscriptionId)
				.collect(Collectors.toList());
	}

	/**
	 * Returns information on a particular subscription.
	 *
	 * @param subscriptionId the id of the subscription
	 * @return the detail about the requested subscription. null when the subscription
	 * does not exist.
	 */
	@Nullable
	public SubscriptionDetail getSubscription(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return new SubscriptionDetail(sub);
		}
		return null;
	}

	/**
	 * Returns a list of session IDs for sessions currently attached to the subscription.
	 *
	 * @param subscriptionId the id of the subscription
	 * @return the list of session IDs attached to the subscription
	 */
	public List<Long> listSubscribers(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return sub.getSubscribers().stream().map(Subscriber::getWampSessionId)
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	/**
	 * Returns the number of sessions currently attached to the subscription.
	 *
	 * @param subscriptionId the subscription id
	 * @return the number of subscriptions or null when the subscription does not exist.
	 */
	@Nullable
	public Integer countSubscribers(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return sub.getSubscribers().size();
		}
		return null;
	}

	/**
	 * Checks if a particular topic currently has attached subscriptions
	 *
	 * @param topic the topic
	 * @return true if currently sessions are attached to the topic
	 */
	public boolean hasSubscribers(String topic) {
		return !getMatchSubscriptions(topic).isEmpty();
	}

}
