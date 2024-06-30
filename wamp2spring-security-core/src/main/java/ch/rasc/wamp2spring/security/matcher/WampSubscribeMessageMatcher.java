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
package ch.rasc.wamp2spring.security.matcher;

import org.springframework.messaging.Message;
import org.springframework.security.messaging.util.matcher.MessageMatcher;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class WampSubscribeMessageMatcher implements MessageMatcher<Object> {

	private final DestinationMatch topicMatch;

	public WampSubscribeMessageMatcher(DestinationMatch topicMatch) {
		this.topicMatch = topicMatch;
	}

	@Override
	public boolean matches(Message<? extends Object> message) {
		if (message instanceof SubscribeMessage) {
			SubscribeMessage subscribeMessage = (SubscribeMessage) message;
			String topic = subscribeMessage.getTopic();
			if (this.topicMatch.getMatchPolicy() != MatchPolicy.PREFIX) {
				return subscribeMessage.getMatchPolicy() == this.topicMatch
						.getMatchPolicy()
						&& topic.equals(this.topicMatch.getDestination());
			}
			return topic.startsWith(this.topicMatch.getDestination());
		}
		return false;
	}

}
