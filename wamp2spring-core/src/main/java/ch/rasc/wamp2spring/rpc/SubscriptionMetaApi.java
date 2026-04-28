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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
import ch.rasc.wamp2spring.pubsub.EventHistoryEntry;
import ch.rasc.wamp2spring.pubsub.EventStore;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;
import ch.rasc.wamp2spring.util.WampUriValidator;

public class SubscriptionMetaApi {

	static final String LIST = "wamp.subscription.list";

	static final String LOOKUP = "wamp.subscription.lookup";

	static final String MATCH = "wamp.subscription.match";

	static final String GET = "wamp.subscription.get";

	static final String LIST_SUBSCRIBERS = "wamp.subscription.list_subscribers";

	static final String COUNT_SUBSCRIBERS = "wamp.subscription.count_subscribers";

	static final String GET_EVENTS = "wamp.subscription.get_events";

	static final String ON_CREATE = "wamp.subscription.on_create";

	static final String ON_SUBSCRIBE = "wamp.subscription.on_subscribe";

	static final String ON_UNSUBSCRIBE = "wamp.subscription.on_unsubscribe";

	static final String ON_DELETE = "wamp.subscription.on_delete";

	private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
		.withZone(ZoneOffset.UTC);

	private final SubscriptionRegistry subscriptionRegistry;

	private final WampPublisher wampPublisher;

	private final EventStore eventStore;

	public SubscriptionMetaApi(SubscriptionRegistry subscriptionRegistry, WampPublisher wampPublisher,
			EventStore eventStore) {
		this.subscriptionRegistry = subscriptionRegistry;
		this.wampPublisher = wampPublisher;
		this.eventStore = eventStore;
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
	public WampResult lookup(CallMessage callMessage) throws WampException {
		MatchPolicy matchPolicy = Objects.requireNonNullElse(matchPolicyOption(callMessage), MatchPolicy.EXACT);
		String topic = stringArgument(callMessage, 0);
		WampUriValidator.validateSubscriptionTopic(topic, matchPolicy);
		Long subscriptionId = this.subscriptionRegistry.lookupSubscription(callMessage.getRealm(), topic, matchPolicy);
		return new WampResult().add(subscriptionId);
	}

	@WampProcedure(MATCH)
	public WampResult match(CallMessage callMessage) throws WampException {
		String topic = stringArgument(callMessage, 0);
		WampUriValidator.validateSubscriptionTopic(topic, MatchPolicy.EXACT);
		return WampResult.create(this.subscriptionRegistry.getMatchSubscriptions(callMessage.getRealm(), topic));
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

	@WampProcedure(GET_EVENTS)
	public WampResult getEvents(CallMessage callMessage) throws WampException {
		SubscriptionDetail detail = requireSubscription(callMessage, longArgument(callMessage, 0));
		Map<String, Object> options = callMessage.getArgumentsKw() != null ? callMessage.getArgumentsKw() : Map.of();
		List<EventHistoryEntry> history = this.eventStore.getHistory(detail.getId());
		List<EventHistoryEntry> filteredHistory = filterHistory(history, detail, callMessage, options);
		List<Map<String, Object>> eventResults = new ArrayList<>(filteredHistory.size());
		for (EventHistoryEntry historyEntry : filteredHistory) {
			eventResults.add(toEventHistory(detail, historyEntry));
		}
		return WampResult.create(eventResults);
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

	private static List<EventHistoryEntry> filterHistory(List<EventHistoryEntry> history, SubscriptionDetail detail,
			CallMessage callMessage, Map<String, Object> options) throws WampException {
		if (history.isEmpty()) {
			return List.of();
		}

		String requestedTopic = optionalString(options.get("topic"));
		if (requestedTopic != null) {
			WampUriValidator.validatePublishTopic(requestedTopic);
		}

		int startIndex = 0;
		int endIndex = history.size() - 1;

		Long fromPublication = optionalLong(options.get("from_publication"));
		if (fromPublication != null) {
			Integer index = publicationIndex(history, fromPublication);
			if (index == null) {
				return List.of();
			}
			startIndex = Math.max(startIndex, index);
		}

		Long afterPublication = optionalLong(options.get("after_publication"));
		if (afterPublication != null) {
			Integer index = publicationIndex(history, afterPublication);
			if (index == null) {
				return List.of();
			}
			startIndex = Math.max(startIndex, index + 1);
		}

		Long beforePublication = optionalLong(options.get("before_publication"));
		if (beforePublication != null) {
			Integer index = publicationIndex(history, beforePublication);
			if (index == null) {
				return List.of();
			}
			endIndex = Math.min(endIndex, index - 1);
		}

		Long untilPublication = optionalLong(options.get("until_publication"));
		if (untilPublication != null) {
			Integer index = publicationIndex(history, untilPublication);
			if (index == null) {
				return List.of();
			}
			endIndex = Math.min(endIndex, index);
		}

		if (startIndex > endIndex) {
			return List.of();
		}

		Instant fromTime = optionalInstant(options.get("from_time"));
		Instant afterTime = optionalInstant(options.get("after_time"));
		Instant beforeTime = optionalInstant(options.get("before_time"));
		Instant untilTime = optionalInstant(options.get("until_time"));
		boolean reverse = optionalBoolean(options.get("reverse"));
		Integer limit = optionalInteger(options.get("limit"));

		List<EventHistoryEntry> events = new ArrayList<>();
		for (int i = startIndex; i <= endIndex; i++) {
			EventHistoryEntry historyEntry = history.get(i);
			if (matchesEventHistory(historyEntry, detail, callMessage, requestedTopic, fromTime, afterTime, beforeTime,
					untilTime)) {
				events.add(historyEntry);
			}
		}

		if (reverse) {
			Collections.reverse(events);
		}

		if (limit != null && limit < events.size()) {
			return List.copyOf(events.subList(0, limit));
		}

		return List.copyOf(events);
	}

	private static boolean matchesEventHistory(EventHistoryEntry historyEntry, SubscriptionDetail detail,
			CallMessage callMessage, @Nullable String requestedTopic, @Nullable Instant fromTime,
			@Nullable Instant afterTime, @Nullable Instant beforeTime, @Nullable Instant untilTime) {
		PublishMessage publishMessage = historyEntry.getPublishMessage();
		if (publishMessage.getEligible() != null || publishMessage.getExclude() != null) {
			return false;
		}

		String authId = callMessage.getAuthId();
		if (publishMessage.getEligibleAuthIds() != null
				&& (authId == null || !publishMessage.getEligibleAuthIds().contains(authId))) {
			return false;
		}
		if (publishMessage.getExcludeAuthIds() != null && authId != null
				&& publishMessage.getExcludeAuthIds().contains(authId)) {
			return false;
		}

		String authRole = callMessage.getAuthRole();
		if (publishMessage.getEligibleAuthRoles() != null
				&& (authRole == null || !publishMessage.getEligibleAuthRoles().contains(authRole))) {
			return false;
		}
		if (publishMessage.getExcludeAuthRoles() != null && authRole != null
				&& publishMessage.getExcludeAuthRoles().contains(authRole)) {
			return false;
		}

		if (requestedTopic != null && !requestedTopic.equals(publishMessage.getTopic())) {
			return false;
		}

		if (detail.getMatchPolicy() == MatchPolicy.EXACT && !detail.getTopic().equals(publishMessage.getTopic())) {
			return false;
		}

		Instant eventInstant = Instant.ofEpochMilli(historyEntry.getTimestampMillis());
		if (fromTime != null && eventInstant.isBefore(fromTime)) {
			return false;
		}
		if (afterTime != null && !eventInstant.isAfter(afterTime)) {
			return false;
		}
		if (beforeTime != null && !eventInstant.isBefore(beforeTime)) {
			return false;
		}
		if (untilTime != null && eventInstant.isAfter(untilTime)) {
			return false;
		}

		return true;
	}

	private static Map<String, Object> toEventHistory(SubscriptionDetail detail, EventHistoryEntry historyEntry) {
		PublishMessage publishMessage = historyEntry.getPublishMessage();
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("timestamp", CREATED_FORMATTER.format(Instant.ofEpochMilli(historyEntry.getTimestampMillis())));
		event.put("subscription", detail.getId());
		event.put("publication", historyEntry.getPublicationId());
		event.put("details", toEventDetails(detail, publishMessage));
		if (publishMessage.getArguments() != null) {
			event.put("args", publishMessage.getArguments());
		}
		if (publishMessage.getArgumentsKw() != null) {
			event.put("kwargs", publishMessage.getArgumentsKw());
		}
		return event;
	}

	private static Map<String, Object> toEventDetails(SubscriptionDetail detail, PublishMessage publishMessage) {
		Map<String, Object> details = new LinkedHashMap<>();
		if (detail.getMatchPolicy() != MatchPolicy.EXACT) {
			details.put("topic", publishMessage.getTopic());
		}
		if (publishMessage.isDiscloseMe() && publishMessage.getWampSessionId() != null) {
			details.put("publisher", publishMessage.getWampSessionId());
			String publisherAuthId = publishMessage.getAuthId();
			if (publisherAuthId != null) {
				details.put("publisher_authid", publisherAuthId);
			}
			String publisherAuthRole = publishMessage.getAuthRole();
			if (publisherAuthRole != null) {
				details.put("publisher_authrole", publisherAuthRole);
			}
			Number trustLevel = publishMessage.getTrustLevel();
			if (trustLevel != null) {
				details.put("trustlevel", trustLevel);
			}
		}
		return details;
	}

	@Nullable private static Integer publicationIndex(List<EventHistoryEntry> history, long publicationId) {
		for (int i = 0; i < history.size(); i++) {
			if (history.get(i).getPublicationId() == publicationId) {
				return i;
			}
		}
		return null;
	}

	@Nullable private static String optionalString(@Nullable Object value) {
		return value != null ? value.toString() : null;
	}

	private static boolean optionalBoolean(@Nullable Object value) {
		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}
		return value != null && Boolean.parseBoolean(value.toString());
	}

	@Nullable private static Integer optionalInteger(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		return Integer.parseInt(value.toString());
	}

	@Nullable private static Long optionalLong(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}

	@Nullable private static Instant optionalInstant(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		return OffsetDateTime.parse(value.toString()).toInstant();
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