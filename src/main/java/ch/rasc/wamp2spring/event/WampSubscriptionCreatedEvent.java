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

package ch.rasc.wamp2spring.event;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;

/**
 * Fired when a subscription is created through a subscription request for a topic which
 * was previously without subscribers.
 * 
 * A {@link WampSubscriptionSubscribedEvent} is always fired after this event, since the
 * first subscribe results in both the creation of the subscription and the addition of a
 * session
 */
public class WampSubscriptionCreatedEvent extends WampSubscriptionEvent {

	public WampSubscriptionCreatedEvent(WampMessage wampMessage,
			SubscriptionDetail subscriptionDetail) {
		super(wampMessage, subscriptionDetail);
	}

}
