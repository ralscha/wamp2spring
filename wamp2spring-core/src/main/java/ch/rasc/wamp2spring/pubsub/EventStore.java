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

import java.util.List;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.PublishMessage;

/**
 * Interface for implementing an event store. Used for event retention.
 *
 * @see MemoryEventStore
 */
public interface EventStore {

	/**
	 * Stores an event in the store
	 * @param publishMessage the event to store
	 */
	void retain(PublishMessage publishMessage);

	/**
	 * Stores a publication in the event history for a concrete subscription.
	 * @param subscriptionId the subscription id the publication matched
	 * @param publicationId the publication id
	 * @param timestampMillis the time the publication was accepted by the broker
	 * @param publishMessage the original publication
	 */
	default void storeHistoryEvent(long subscriptionId, long publicationId, long timestampMillis,
			PublishMessage publishMessage) {
		// optional
	}

	/**
	 * Returns all stored events that match the query.
	 * @param query the query
	 * @return a collection of events that match the query
	 */
	List<PublishMessage> getRetained(DestinationMatch query);

	/**
	 * Returns stored history events for a concrete subscription.
	 * @param subscriptionId the subscription id
	 * @return the stored events in order of occurrence
	 */
	default List<EventHistoryEntry> getHistory(long subscriptionId) {
		return List.of();
	}

	/**
	 * Deletes stored history events for a concrete subscription.
	 * @param subscriptionId the subscription id
	 */
	default void deleteHistory(long subscriptionId) {
		// optional
	}

}
