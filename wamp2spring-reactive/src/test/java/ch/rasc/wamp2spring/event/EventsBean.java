/**
 * Copyright 2018-2018 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class EventsBean {

	private final Map<String, List<WampEvent>> methodCounter = new HashMap<>();

	@EventListener
	public void sessionEstablished(WampSessionEstablishedEvent evt) {
		this.methodCounter.computeIfAbsent("sessionEstablished", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void disconnected(WampDisconnectEvent evt) {
		this.methodCounter.computeIfAbsent("disconnected", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void procedureRegistered(WampProcedureRegisteredEvent evt) {
		this.methodCounter.computeIfAbsent("procedureRegistered", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void procedureUnregistered(WampProcedureUnregisteredEvent evt) {
		this.methodCounter
				.computeIfAbsent("procedureUnregistered", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void subscriptionCreated(WampSubscriptionCreatedEvent evt) {
		this.methodCounter.computeIfAbsent("subscriptionCreated", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void subscriptionDeleted(WampSubscriptionDeletedEvent evt) {
		this.methodCounter.computeIfAbsent("subscriptionDeleted", k -> new ArrayList<>())
				.add(evt);
	}

	@EventListener
	public void subscribed(WampSubscriptionSubscribedEvent evt) {
		this.methodCounter.computeIfAbsent("subscribed", k -> new ArrayList<>()).add(evt);
	}

	@EventListener
	public void unsubscribed(WampSubscriptionUnsubscribedEvent evt) {
		this.methodCounter.computeIfAbsent("unsubscribed", k -> new ArrayList<>())
				.add(evt);
	}

	public Map<String, List<WampEvent>> getMethodCounter() {
		return this.methodCounter;
	}

	public void resetCounter() {
		this.methodCounter.clear();
	}

}
