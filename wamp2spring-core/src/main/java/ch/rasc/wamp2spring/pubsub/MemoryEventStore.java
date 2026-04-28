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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.PublishMessage;

/**
 * Memory only implementation of the {@link EventStore} interface. Used for storing
 * retention messages. Only publishMessages coming from the internal {@link WampPublisher}
 * are stored in the retention store.
 */
public class MemoryEventStore implements EventStore {

	protected final Map<String, PublishMessage> eventRetention = new ConcurrentHashMap<>();

	protected final Map<Long, CopyOnWriteArrayList<EventHistoryEntry>> eventHistory = new ConcurrentHashMap<>();

	@Override
	public void retain(PublishMessage publishMessage) {
		if (publishMessage.getWebSocketSessionId() == null) {
			this.eventRetention.put(publishMessage.getTopic(), publishMessage);
		}
	}

	@Override
	public List<PublishMessage> getRetained(DestinationMatch query) {
		if (query.getMatchPolicy() == MatchPolicy.EXACT) {
			PublishMessage publishMessage = this.eventRetention.get(query.getDestination());
			if (publishMessage != null) {
				return List.of(publishMessage);
			}
			return List.of();
		}
		if (query.getMatchPolicy() == MatchPolicy.PREFIX || query.getMatchPolicy() == MatchPolicy.WILDCARD) {
			List<PublishMessage> matchedMessages = new ArrayList<>();
			this.eventRetention.forEach((topic, publishMessage) -> {
				if (query.matches(topic)) {
					matchedMessages.add(publishMessage);
				}
			});
			return List.copyOf(matchedMessages);
		}

		return List.of();
	}

	@Override
	public void storeHistoryEvent(long subscriptionId, long publicationId, long timestampMillis,
			PublishMessage publishMessage) {
		this.eventHistory.computeIfAbsent(subscriptionId, key -> new CopyOnWriteArrayList<>())
			.add(new EventHistoryEntry(publicationId, timestampMillis, publishMessage));
	}

	@Override
	public List<EventHistoryEntry> getHistory(long subscriptionId) {
		List<EventHistoryEntry> entries = this.eventHistory.get(subscriptionId);
		if (entries == null) {
			return List.of();
		}
		return List.copyOf(entries);
	}

	@Override
	public void deleteHistory(long subscriptionId) {
		this.eventHistory.remove(subscriptionId);
	}

}
