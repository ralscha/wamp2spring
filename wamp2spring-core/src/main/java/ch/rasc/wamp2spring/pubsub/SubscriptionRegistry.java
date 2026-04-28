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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * In memory subscription registry
 */
public class SubscriptionRegistry {

	private final AtomicLong lastSubscriptionId = new AtomicLong(1L);

	private final EnumMap<MatchPolicy, Map<SubscriptionKey, Subscription>> subscriptionsByMatch = new EnumMap<>(
			MatchPolicy.class);

	private final Map<Long, Subscription> subscriptionsById = new ConcurrentHashMap<>();

	private final LoadingCache<SubscriptionCacheKey, Set<Subscription>> subscriptionsCache = Caffeine.newBuilder()
		.maximumSize(512)
		.build(this::internalFindSubscriptions);

	private final Object monitor = new Object();

	public SubscriptionRegistry() {
		this.subscriptionsByMatch.put(MatchPolicy.EXACT, new ConcurrentHashMap<>());
		this.subscriptionsByMatch.put(MatchPolicy.PREFIX, new ConcurrentHashMap<>());
		this.subscriptionsByMatch.put(MatchPolicy.WILDCARD, new ConcurrentHashMap<>());
	}

	SubscribeResult subscribe(SubscribeMessage subscribeMessage) {
		Map<SubscriptionKey, Subscription> subscriptionMap = subscriptionsFor(subscribeMessage.getMatchPolicy());
		SubscriptionKey subscriptionKey = new SubscriptionKey(subscribeMessage.getRealm(), subscribeMessage.getTopic());

		boolean created = false;

		Subscription subscription = subscriptionMap.get(subscriptionKey);
		if (subscription == null) {
			synchronized (this.monitor) {
				subscription = subscriptionMap.get(subscriptionKey);
				if (subscription == null) {
					long subscriptionId = IdGenerator.newLinearId(this.lastSubscriptionId);
					subscription = new Subscription(subscribeMessage.getRealm(), subscribeMessage.getTopic(),
							subscribeMessage.getMatchPolicy(), subscriptionId, subscribeMessage.getOptions());
					subscriptionMap.put(subscriptionKey, subscription);
					this.subscriptionsById.put(subscriptionId, subscription);
					created = true;
					invalidateCacheEntries(subscription);
				}
			}
		}
		Subscriber subscriber = new Subscriber(requireWebSocketSessionId(subscribeMessage),
				requireWampSessionId(subscribeMessage), subscribeMessage.getAuthId(), subscribeMessage.getAuthRole(),
				supportsSubscriptionRevocation(subscribeMessage));
		subscription.addSubscriber(subscriber);

		return new SubscribeResult(requireWampSessionId(subscribeMessage), subscription, created);
	}

	void subscribeEventHandlers(List<EventListenerInfo> eventListeners) {
		for (EventListenerInfo eventListener : eventListeners) {
			Map<SubscriptionKey, Subscription> subscriptionMap = subscriptionsFor(eventListener.getMatch());
			for (String topic : eventListener.getTopic()) {
				SubscriptionKey subscriptionKey = new SubscriptionKey(null, topic);
				synchronized (this.monitor) {
					Subscription subscription = subscriptionMap.get(subscriptionKey);
					if (subscription == null) {
						long subscriptionId = IdGenerator.newLinearId(this.lastSubscriptionId);
						subscription = new Subscription(null, topic, eventListener.getMatch(), subscriptionId, null);
						subscriptionMap.put(subscriptionKey, subscription);
						this.subscriptionsById.put(subscriptionId, subscription);
						invalidateCacheEntries(subscription);
					}
					subscription.addEventListenerHandlerMethod(eventListener.getHandlerMethod());
				}
			}
		}
	}

	UnsubscribeResult unsubscribe(UnsubscribeMessage message) {
		Subscription subscription = this.subscriptionsById.get(message.getSubscriptionId());

		if (subscription != null) {
			Subscriber subscriber = new Subscriber(requireWebSocketSessionId(message), requireWampSessionId(message));

			synchronized (this.monitor) {
				if (subscription.removeSubscriber(subscriber)) {
					boolean deleted = false;
					if (!subscription.hasSubscribers()) {
						subscriptionsFor(subscription.getMatchPolicy())
							.remove(new SubscriptionKey(subscription.getRealm(), subscription.getTopic()));
						this.subscriptionsById.remove(subscription.getSubscriptionId());
						deleted = true;
						invalidateCacheEntries(subscription);
					}
					return new UnsubscribeResult(requireWampSessionId(message), subscription, deleted);
				}
			}
		}

		return new UnsubscribeResult(requireWampSessionId(message), WampError.NO_SUCH_SUBSCRIPTION);
	}

	List<SubscriptionRevocation> revokeSubscription(long subscriptionId) {
		Subscription subscription = this.subscriptionsById.get(subscriptionId);
		if (subscription == null) {
			return List.of();
		}

		synchronized (this.monitor) {
			List<Subscriber> subscribers = new ArrayList<>(subscription.getSubscribers());
			if (subscribers.isEmpty()) {
				return List.of();
			}

			for (Subscriber subscriber : subscribers) {
				subscription.removeSubscriber(subscriber);
			}

			boolean deleted = false;
			if (!subscription.hasSubscribers()) {
				subscriptionsFor(subscription.getMatchPolicy())
					.remove(new SubscriptionKey(subscription.getRealm(), subscription.getTopic()));
				this.subscriptionsById.remove(subscription.getSubscriptionId());
				invalidateCacheEntries(subscription);
				deleted = true;
			}

			List<SubscriptionRevocation> revocations = new ArrayList<>(subscribers.size());
			for (int i = 0; i < subscribers.size(); i++) {
				revocations.add(new SubscriptionRevocation(subscribers.get(i), subscription,
						deleted && i == subscribers.size() - 1));
			}
			return revocations;
		}
	}

	List<UnsubscribeResult> removeWebSocketSessionId(String webSocketSessionId, long wampSessionId) {
		List<UnsubscribeResult> results = new ArrayList<>();
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			Map<SubscriptionKey, Subscription> subscriptionMap = subscriptionsFor(matchPolicy);

			for (Iterator<Map.Entry<SubscriptionKey, Subscription>> iterator = subscriptionMap.entrySet()
				.iterator(); iterator.hasNext();) {
				Map.Entry<SubscriptionKey, Subscription> entry = iterator.next();
				Subscription subscription = entry.getValue();
				Subscriber subscriber = new Subscriber(webSocketSessionId, wampSessionId);

				synchronized (this.monitor) {
					if (subscription.removeSubscriber(subscriber)) {
						boolean deleted = false;
						if (!subscription.hasSubscribers()) {
							iterator.remove();
							this.subscriptionsById.remove(subscription.getSubscriptionId());
							deleted = true;
							invalidateCacheEntries(subscription);
						}

						results.add(new UnsubscribeResult(wampSessionId, subscription, deleted));
					}
				}
			}

		}
		return results;
	}

	Set<Subscription> findSubscriptions(String topic) {
		return findSubscriptions((String) null, topic);
	}

	Set<Subscription> findSubscriptions(WampMessage message, String topic) {
		String realm = message.getRealm();
		if (realm == null) {
			return internalFindSubscriptionsForAnyRealm(topic);
		}

		Set<Subscription> subscriptions = new HashSet<>(findSubscriptions(realm, topic));
		subscriptions.addAll(internalFindGlobalEventHandlerSubscriptions(topic));
		return subscriptions;
	}

	Set<Subscription> findSubscriptions(@Nullable String realm, String topic) {
		return this.subscriptionsCache.get(new SubscriptionCacheKey(realm, topic));
	}

	private Set<Subscription> internalFindSubscriptions(SubscriptionCacheKey cacheKey) {
		Set<Subscription> subscriptions = new HashSet<>();
		String topic = cacheKey.topic();

		Subscription exactSubscription = subscriptionsFor(MatchPolicy.EXACT)
			.get(new SubscriptionKey(cacheKey.realm(), topic));
		if (exactSubscription != null) {
			subscriptions.add(exactSubscription);
		}

		Map<SubscriptionKey, Subscription> prefixSubscriptionMap = subscriptionsFor(MatchPolicy.PREFIX);
		for (Subscription prefixSubscription : prefixSubscriptionMap.values()) {
			if (!Objects.equals(cacheKey.realm(), prefixSubscription.getRealm())) {
				continue;
			}
			if (prefixSubscription.getTopicMatch().matches(topic)) {
				subscriptions.add(prefixSubscription);
			}
		}

		Map<SubscriptionKey, Subscription> wildcardSubscriptionMap = subscriptionsFor(MatchPolicy.WILDCARD);
		String[] components = topic.split("\\.");
		for (Subscription wildcardSubscription : wildcardSubscriptionMap.values()) {
			if (!Objects.equals(cacheKey.realm(), wildcardSubscription.getRealm())) {
				continue;
			}
			if (wildcardSubscription.getTopicMatch().matchesWildcard(components)) {
				subscriptions.add(wildcardSubscription);
			}
		}

		return subscriptions;
	}

	private Set<Subscription> internalFindSubscriptionsForAnyRealm(String topic) {
		Set<Subscription> subscriptions = new HashSet<>();
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			for (Subscription subscription : subscriptionsFor(matchPolicy).values()) {
				if (matches(subscription, topic)) {
					subscriptions.add(subscription);
				}
			}
		}
		return subscriptions;
	}

	private Set<Subscription> internalFindGlobalEventHandlerSubscriptions(String topic) {
		Set<Subscription> subscriptions = new HashSet<>();
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			for (Subscription subscription : subscriptionsFor(matchPolicy).values()) {
				if (subscription.getRealm() == null && subscription.getEventListenerHandlerMethods() != null
						&& matches(subscription, topic)) {
					subscriptions.add(subscription);
				}
			}
		}
		return subscriptions;
	}

	private static boolean matches(Subscription subscription, String topic) {
		if (subscription.getMatchPolicy() == MatchPolicy.EXACT) {
			return subscription.getTopic().equals(topic);
		}
		if (subscription.getMatchPolicy() == MatchPolicy.PREFIX) {
			return subscription.getTopicMatch().matches(topic);
		}
		return subscription.getTopicMatch().matchesWildcard(topic.split("\\."));
	}

	private void invalidateCacheEntries(Subscription subscription) {
		if (subscription.getMatchPolicy() == MatchPolicy.EXACT) {
			this.subscriptionsCache
				.invalidate(new SubscriptionCacheKey(subscription.getRealm(), subscription.getTopic()));
		}
		else {
			DestinationMatch topicMatch = subscription.getTopicMatch();
			this.subscriptionsCache.asMap()
				.keySet()
				.removeIf(cacheKey -> Objects.equals(cacheKey.realm(), subscription.getRealm())
						&& topicMatch.matches(cacheKey.topic()));
		}
	}

	private static boolean supportsSubscriptionRevocation(SubscribeMessage subscribeMessage) {
		if (subscribeMessage.getPeerRoles() == null) {
			return false;
		}

		return subscribeMessage.getPeerRoles()
			.stream()
			.filter(role -> "subscriber".equals(role.getRole()))
			.anyMatch(role -> role.getFeatures().contains("subscription_revocation"));
	}

	/**
	 * Returns subscription IDs listed according to matching policies.
	 * @return subscription IDs grouped by matching policies
	 */
	public EnumMap<MatchPolicy, List<Long>> listSubscriptions() {
		return listSubscriptions(null);
	}

	public EnumMap<MatchPolicy, List<Long>> listSubscriptions(@Nullable String realm) {
		EnumMap<MatchPolicy, List<Long>> result = new EnumMap<>(MatchPolicy.class);

		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			List<Long> subscriptionIds = subscriptionsFor(matchPolicy).values()
				.stream()
				.filter(subscription -> Objects.equals(realm, subscription.getRealm()))
				.map(Subscription::getSubscriptionId)
				.toList();
			result.put(matchPolicy, subscriptionIds);
		}

		return result;
	}

	/**
	 * Returns the subscription ID (if any) managing a topic, according to the matching
	 * policy.
	 * @param topic the topic URI
	 * @param matchPolicy the matching policy
	 * @return the subscription id or null if no matching subscription exist
	 */
	@Nullable public Long lookupSubscription(String topic, @Nullable MatchPolicy matchPolicy) {
		return lookupSubscription(null, topic, matchPolicy);
	}

	@Nullable public Long lookupSubscription(@Nullable String realm, String topic, @Nullable MatchPolicy matchPolicy) {
		MatchPolicy me = matchPolicy;
		if (me == null) {
			me = MatchPolicy.EXACT;
		}

		Subscription subscription = subscriptionsFor(me).get(new SubscriptionKey(realm, topic));
		if (subscription != null) {
			return subscription.getSubscriptionId();
		}
		return null;
	}

	private Map<SubscriptionKey, Subscription> subscriptionsFor(MatchPolicy matchPolicy) {
		return Objects.requireNonNull(this.subscriptionsByMatch.get(matchPolicy));
	}

	private static String requireWebSocketSessionId(WampMessage message) {
		return Objects.requireNonNull(message.getWebSocketSessionId());
	}

	private static long requireWampSessionId(WampMessage message) {
		return Objects.requireNonNull(message.getWampSessionId());
	}

	/**
	 * Returns a list of IDs of subscriptions matching a topic URI, irrespective of match
	 * policy.
	 * @param topic the topic URI
	 * @return the list of session IDs subscribed to the topic
	 */
	public List<Long> getMatchSubscriptions(String topic) {
		return getMatchSubscriptions(null, topic);
	}

	public List<Long> getMatchSubscriptions(@Nullable String realm, String topic) {
		return findSubscriptions(realm, topic).stream().map(Subscription::getSubscriptionId).toList();
	}

	/**
	 * Returns information on a particular subscription.
	 * @param subscriptionId the id of the subscription
	 * @return the detail about the requested subscription. null when the subscription
	 * does not exist.
	 */
	@Nullable public SubscriptionDetail getSubscription(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return new SubscriptionDetail(sub);
		}
		return null;
	}

	/**
	 * Returns a list of session IDs for sessions currently attached to the subscription.
	 * @param subscriptionId the id of the subscription
	 * @return the list of session IDs attached to the subscription
	 */
	public List<Long> listSubscribers(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return sub.getSubscribers().stream().map(Subscriber::getWampSessionId).toList();
		}
		return List.of();
	}

	/**
	 * Returns the number of sessions currently attached to the subscription.
	 * @param subscriptionId the subscription id
	 * @return the number of subscriptions or null when the subscription does not exist.
	 */
	@Nullable public Integer countSubscribers(long subscriptionId) {
		Subscription sub = this.subscriptionsById.get(subscriptionId);
		if (sub != null) {
			return sub.getSubscribers().size();
		}
		return null;
	}

	/**
	 * Checks if a particular topic currently has attached subscriptions
	 * @param topic the topic
	 * @return true if currently sessions are attached to the topic
	 */
	public boolean hasSubscribers(String topic) {
		return !getMatchSubscriptions(topic).isEmpty();
	}

	private record SubscriptionKey(@Nullable String realm, String topic) {
		// map key
	}

	private record SubscriptionCacheKey(@Nullable String realm, String topic) {
		// cache key
	}

}
