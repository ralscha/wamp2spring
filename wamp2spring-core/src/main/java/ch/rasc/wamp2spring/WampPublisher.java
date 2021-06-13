/**
 * Copyright 2017-2021 the original author or authors.
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
package ch.rasc.wamp2spring;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.lang.Nullable;
import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishMessage.Builder;
import ch.rasc.wamp2spring.util.CollectionHelper;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * A publisher that allows the calling code to send {@link PublishMessage}s to the Broker.
 * The WampPublisher is by default configured as a Spring managed bean and can be
 * autowired into any other spring bean.
 *
 * e.g.
 *
 * <pre class="code">
 * &#64;Service
 * public class MyService {
 * 	private final WampPublisher wampPublisher;
 *
 * 	public MyService(WampPublisher wampPublisher) {
 * 		this.wampPublisher = wampPublisher;
 * 	}
 * }
 * </pre>
 */
@SuppressWarnings("unchecked")
public class WampPublisher {
	private final MessageChannel clientInboundChannel;

	private final AtomicLong atomicLong = new AtomicLong();

	/**
	 * Creates a new WAMP publisher that sends events over the provided channel
	 */
	public WampPublisher(MessageChannel clientInboundChannel) {
		this.clientInboundChannel = clientInboundChannel;
	}

	/**
	 * Sends an arbitrary {@link PublishMessage} to the Broker.
	 * @param publishMessage the {@link PublishMessage} instance
	 */
	public void publish(PublishMessage publishMessage) {
		this.clientInboundChannel.send(publishMessage);
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic
	 *
	 * @param topic the topic to send the event to
	 * @param arguments a variable number of event arguments
	 */
	public <T> void publishToAll(String topic, @Nullable T... arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic
	 *
	 * @param topic the topic to send the event to
	 * @param arguments a collection of event arguments
	 */
	public <T> void publishToAll(String topic, @Nullable Collection<T> arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic
	 *
	 * @param topic the topic to send the event to
	 * @param arguments a map with event arguments
	 */
	public <T> void publishToAll(String topic, @Nullable Map<String, T> arguments) {
		publish(publishMessageBuilder(topic).arguments((Map<String, Object>) arguments)
				.build());
	}

	/**
	 * Creates a new event and sends it to a specific group of Subscribers of the topic
	 *
	 * @param eligibleWampSessionIds the collection of WAMP session ids to send the event
	 * to
	 * @param topic the topic to send the event to
	 * @param arguments a variable number of event arguments
	 */
	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable T... arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to a specific group of Subscribers of the topic
	 * @param eligibleWampSessionIds
	 * @param topic the topic to send the event to
	 * @param arguments a collection of event arguments
	 */
	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable Collection<T> arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to a specific group of Subscribers of the topic
	 *
	 * @param eligibleWampSessionIds
	 * @param topic the topic to send the event to
	 * @param arguments a map with event arguments
	 */
	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable Map<String, T> arguments) {
		publish(publishMessageBuilder(topic).arguments((Map<String, Object>) arguments)
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to a one specific Subscriber of the topic
	 *
	 * @param eligibleWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a variable number of event arguments
	 */
	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable T... arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addEligible(eligibleWampSessionId).build());
	}

	/**
	 * Creates a new event and sends it to a one specific Subscriber of the topic
	 *
	 * @param eligibleWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a collection of event arguments
	 */
	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable Collection<T> arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addEligible(eligibleWampSessionId).build());
	}

	/**
	 * Creates a new event and sends it to a one specific Subscriber of the topic
	 *
	 * @param eligibleWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a map with event arguments
	 */
	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable Map<String, T> arguments) {
		publish(publishMessageBuilder(topic).arguments((Map<String, Object>) arguments)
				.addEligible(eligibleWampSessionId).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to
	 * Subscribers that are listed in the provided collection of WAMP session ids
	 *
	 * @param excludeWampSessionIds
	 * @param topic the topic to send the event to
	 * @param arguments a variable number of event arguments
	 */
	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable T... arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to
	 * Subscribers that are listed in the provided collection of WAMP session ids
	 *
	 * @param excludeWampSessionIds
	 * @param topic the topic to send the event to
	 * @param arguments a collection of event arguments
	 */
	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable Collection<T> arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to
	 * Subscribers that are listed in the provided collection of WAMP session ids
	 *
	 * @param excludeWampSessionIds
	 * @param topic the topic to send the event to
	 * @param arguments a map with event arguments
	 */
	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable Map<String, T> arguments) {
		publish(publishMessageBuilder(topic).arguments((Map<String, Object>) arguments)
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to the
	 * Subscriber with the provided WAMP session id
	 *
	 * @param excludeWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a variable number of event arguments
	 */
	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable T... arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addExclude(excludeWampSessionId).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to the
	 * Subscriber with the provided WAMP session id
	 *
	 * @param excludeWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a collection of event arguments
	 */
	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable Collection<T> arguments) {
		publish(publishMessageBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addExclude(excludeWampSessionId).build());
	}

	/**
	 * Creates a new event and sends it to all Subscribers of the topic except to the
	 * Subscriber with the provided WAMP session id
	 *
	 * @param excludeWampSessionId
	 * @param topic the topic to send the event to
	 * @param arguments a map with event arguments
	 */
	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable Map<String, T> arguments) {
		publish(publishMessageBuilder(topic).arguments((Map<String, Object>) arguments)
				.addExclude(excludeWampSessionId).build());
	}

	/**
	 * Creates a new builder for a {@link PublishMessage}
	 *
	 * @param topic the topic
	 * @return the {@link PublishMessage} builder instance
	 */
	public Builder publishMessageBuilder(String topic) {
		long requestId = IdGenerator.newLinearId(this.atomicLong);
		return PublishMessage.builder(requestId, topic);
	}

}
