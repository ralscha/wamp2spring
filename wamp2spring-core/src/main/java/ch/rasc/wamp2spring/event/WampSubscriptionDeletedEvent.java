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
package ch.rasc.wamp2spring.event;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;

/**
 * Fired when a subscription is deleted after the last session attached to it has been
 * removed.
 *
 * This event is always preceded by a {@link WampSubscriptionUnsubscribedEvent} event.
 */
public class WampSubscriptionDeletedEvent extends WampSubscriptionEvent {

	public WampSubscriptionDeletedEvent(WampMessage wampMessage,
			SubscriptionDetail subscriptionDetail) {
		super(wampMessage, subscriptionDetail);
	}

	public WampSubscriptionDeletedEvent(WampDisconnectEvent event,
			SubscriptionDetail detail) {
		super(event.getWampSessionId(), event.getWebSocketSessionId(),
				event.getPrincipal(), detail);
	}

}
