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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class MemoryEventStore implements EventStore {

	protected final Map<String, PublishMessage> eventRetention = new ConcurrentHashMap<>();

	@Override
	public void retain(PublishMessage publishMessage) {
		if (publishMessage.getWebSocketSessionId() == null) {
			this.eventRetention.put(publishMessage.getTopic(), publishMessage);
		}
	}

	@Override
	public List<PublishMessage> getRetained(DestinationMatch query) {
		if (query.getMatchPolicy() == MatchPolicy.EXACT) {
			PublishMessage publishMessage = this.eventRetention
					.get(query.getDestination());
			if (publishMessage != null) {
				return Collections.singletonList(publishMessage);
			}
			return Collections.emptyList();
		}
		else if (query.getMatchPolicy() == MatchPolicy.PREFIX) {
			List<PublishMessage> matchedMessages = new ArrayList<>();
			this.eventRetention.forEach((topic, publishMessage) -> {
				if (query.matches(topic)) {
					matchedMessages.add(publishMessage);
				}
			});
			return matchedMessages;
		}
		else if (query.getMatchPolicy() == MatchPolicy.WILDCARD) {
			List<PublishMessage> matchedMessages = new ArrayList<>();
			this.eventRetention.forEach((topic, publishMessage) -> {
				if (query.matches(topic)) {
					matchedMessages.add(publishMessage);
				}
			});
			return matchedMessages;
		}

		return Collections.emptyList();
	}

}
