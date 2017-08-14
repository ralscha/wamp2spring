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
package ch.rasc.wampspring.event;

import java.security.Principal;

import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.pubsub.SubscriptionDetail;

public class WampSubscriptionEvent extends WampEvent {
	private final SubscriptionDetail subscriptionDetail;

	public WampSubscriptionEvent(WampMessage wampMessage,
			SubscriptionDetail subscriptionDetail) {
		super(wampMessage.getWampSessionId(), wampMessage.getWebSocketSessionId(),
				wampMessage.getPrincipal());
		this.subscriptionDetail = subscriptionDetail;
	}

	public WampSubscriptionEvent(Long wampSessionId, String webSocketSessionId,
			Principal principal, SubscriptionDetail subscriptionDetail) {
		super(wampSessionId, webSocketSessionId, principal);
		this.subscriptionDetail = subscriptionDetail;
	}

	public SubscriptionDetail getSubscriptionDetail() {
		return this.subscriptionDetail;
	}

}
