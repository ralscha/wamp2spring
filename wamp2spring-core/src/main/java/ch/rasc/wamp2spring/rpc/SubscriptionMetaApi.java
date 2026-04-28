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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.event.WampSubscriptionCreatedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionDeletedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionSubscribedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionUnsubscribedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;

public class SubscriptionMetaApi {

	static final String LIST = "wamp.subscription.list";

	static final String LOOKUP = "wamp.subscription.lookup";

	static final String MATCH = "wamp.subscription.match";

	static final String GET = "wamp.subscription.get";

	static final String LIST_SUBSCRIBERS = "wamp.subscription.list_subscribers";

	static final String COUNT_SUBSCRIBERS = "wamp.subscription.count_subscribers";

	static final String ON_CREATE = "wamp.subscription.on_create";

	static final String ON_SUBSCRIBE = "wamp.subscription.on_subscribe";

	static final String ON_UNSUBSCRIBE = "wamp.subscription.on_unsubscribe";

	static final String ON_DELETE = "wamp.subscription.on_delete";

	private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
		.withZone(ZoneOffset.UTC);

	private final SubscriptionRegistry subscriptionRegistry;

	private final WampPublisher wampPublisher;

	public SubscriptionMetaApi(SubscriptionRegistry subscriptionRegistry, WampPublisher wampPublisher) {
		this.subscriptionRegistry = subscriptionRegistry;
		this.wampPublisher = wampPublisher;
	}

	public WampResult list() {
		return list((String) null);
	}

	@WampProcedure(LIST)
	public WampResult list(CallMessage callMessage) {
		return list(callMessage.getRealm());
	}

	private WampResult list(@Nullable String realm) {
		Map<String, List<Long>> result = new LinkedHashMap<>();
		Map<MatchPolicy, List<Long>> subscriptions = this.subscriptionRegistry.listSubscriptions(realm);
		result.put("exact", subscriptions.get(MatchPolicy.EXACT));
		result.put("prefix", subscriptions.get(MatchPolicy.PREFIX));
		result.put("wildcard", subscriptions.get(MatchPolicy.WILDCARD));
		return WampResult.create(result);
	}

	@WampProcedure(LOOKUP)
	public WampResult lookup(CallMessage callMessage) {
		Long subscriptionId = this.subscriptionRegistry.lookupSubscription(callMessage.getRealm(),
				stringArgument(callMessage, 0), matchPolicyOption(callMessage));
		return new WampResult().add(subscriptionId);
	}

	@WampProcedure(MATCH)
	public WampResult match(CallMessage callMessage) {
		return WampResult.create(this.subscriptionRegistry.getMatchSubscriptions(callMessage.getRealm(),
				stringArgument(callMessage, 0)));
	}

	@WampProcedure(GET)
	public WampResult get(CallMessage callMessage) throws WampException {
		return WampResult.create(toMetaDetail(requireSubscription(callMessage, longArgument(callMessage, 0))));
	}

	@WampProcedure(LIST_SUBSCRIBERS)
	public WampResult listSubscribers(CallMessage callMessage) throws WampException {
		long subscriptionId = longArgument(callMessage, 0);
		requireSubscription(callMessage, subscriptionId);
		return WampResult.create(this.subscriptionRegistry.listSubscribers(subscriptionId));
	}

	@WampProcedure(COUNT_SUBSCRIBERS)
	public WampResult countSubscribers(CallMessage callMessage) throws WampException {
		SubscriptionDetail detail = requireSubscription(callMessage, longArgument(callMessage, 0));
		Integer subscriberCount = this.subscriptionRegistry.countSubscribers(detail.getId());
		if (subscriberCount == null) {
			throw noSuchSubscription();
		}
		return WampResult.create(subscriberCount);
	}

	@EventListener
	public void onSubscriptionCreated(WampSubscriptionCreatedEvent event) {
		publishEvent(event.getRealm(), ON_CREATE, event.getWampSessionId(),
				toMetaDetail(event.getSubscriptionDetail()));
	}

	@EventListener
	public void onSubscriptionSubscribed(WampSubscriptionSubscribedEvent event) {
		publishEvent(event.getRealm(), ON_SUBSCRIBE, event.getWampSessionId(), event.getSubscriptionDetail().getId());
	}

	@EventListener
	public void onSubscriptionUnsubscribed(WampSubscriptionUnsubscribedEvent event) {
		publishEvent(event.getRealm(), ON_UNSUBSCRIBE, event.getWampSessionId(), event.getSubscriptionDetail().getId());
	}

	@EventListener
	public void onSubscriptionDeleted(WampSubscriptionDeletedEvent event) {
		publishEvent(event.getRealm(), ON_DELETE, event.getWampSessionId(), event.getSubscriptionDetail().getId());
	}

	private void publishEvent(@Nullable String realm, String topic, @Nullable Object first, Object second) {
		List<Object> arguments = new ArrayList<>(2);
		arguments.add(first);
		arguments.add(second);
		PublishMessage publishMessage = this.wampPublisher.publishMessageBuilder(topic).arguments(arguments).build();
		publishMessage.setHeader(WampMessageHeader.WAMP_REALM, realm);
		this.wampPublisher.publish(publishMessage);
	}

	private SubscriptionDetail requireSubscription(CallMessage callMessage, long subscriptionId) throws WampException {
		SubscriptionDetail detail = this.subscriptionRegistry.getSubscription(subscriptionId);
		if (detail == null || !Objects.equals(callMessage.getRealm(), detail.getRealm())) {
			throw noSuchSubscription();
		}
		return detail;
	}

	private static WampException noSuchSubscription() {
		return new WampException.Builder().build(WampError.NO_SUCH_SUBSCRIPTION.getExternalValue());
	}

	private static String stringArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		return value.toString();
	}

	private static long longArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static MatchPolicy matchPolicyOption(CallMessage callMessage) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() < 2 || !(arguments.get(1) instanceof Map<?, ?>)) {
			return MatchPolicy.EXACT;
		}

		String match = (String) ((Map<String, Object>) arguments.get(1)).get("match");
		if (match == null) {
			return MatchPolicy.EXACT;
		}
		MatchPolicy matchPolicy = MatchPolicy.fromExtValue(match);
		return matchPolicy != null ? matchPolicy : MatchPolicy.EXACT;
	}

	private static Object argument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			throw new IllegalArgumentException("missing call argument");
		}
		return arguments.get(index);
	}

	private static Map<String, Object> toMetaDetail(SubscriptionDetail detail) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", detail.getId());
		result.put("realm", detail.getRealm());
		result.put("created", CREATED_FORMATTER.format(Instant.ofEpochMilli(detail.getCreatedTimeMillis())));
		result.put("uri", detail.getTopic());
		result.put("match", detail.getMatchPolicy().getExternalValue());
		return result;
	}

}