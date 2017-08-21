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
package ch.rasc.wamp2spring;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishMessage.Builder;
import ch.rasc.wamp2spring.util.CollectionHelper;
import ch.rasc.wamp2spring.util.IdGenerator;

@SuppressWarnings("unchecked")
public class WampPublisher {
	private final MessageChannel clientInboundChannel;

	private final AtomicLong atomicLong = new AtomicLong();

	public WampPublisher(MessageChannel clientInboundChannel) {
		this.clientInboundChannel = clientInboundChannel;
	}

	public void publish(PublishMessage publishMessage) {
		this.clientInboundChannel.send(publishMessage);
	}

	public <T> void publishToAll(String topic, @Nullable T... arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.build());
	}

	public <T> void publishToAll(String topic, @Nullable Collection<T> arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.build());
	}

	public <T> void publishToAll(String topic, @Nullable Map<String, T> arguments) {
		publish(createBuilder(topic).arguments((Map<String, Object>) arguments).build());
	}

	public void publishToAll(String topic, @Nullable Collection<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.arguments(argumentsKw).build());
	}

	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable T... arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable Collection<T> arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	public <T> void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable Map<String, T> arguments) {
		publish(createBuilder(topic).arguments((Map<String, Object>) arguments)
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	public void publishTo(Collection<Long> eligibleWampSessionIds, String topic,
			@Nullable Collection<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.arguments(argumentsKw)
				.eligible(CollectionHelper.toSet(eligibleWampSessionIds)).build());
	}

	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable T... arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addEligible(eligibleWampSessionId).build());
	}

	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable Collection<T> arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addEligible(eligibleWampSessionId).build());
	}

	public <T> void publishTo(long eligibleWampSessionId, String topic,
			@Nullable Map<String, T> arguments) {
		publish(createBuilder(topic).arguments((Map<String, Object>) arguments)
				.addEligible(eligibleWampSessionId).build());
	}

	public void publishTo(long eligibleWampSessionId, String topic,
			@Nullable Collection<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.arguments(argumentsKw).addEligible(eligibleWampSessionId).build());
	}

	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable T... arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable Collection<T> arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	public <T> void publishToAllExcept(Collection<Long> excludeWampSessionIds,
			String topic, @Nullable Map<String, T> arguments) {
		publish(createBuilder(topic).arguments((Map<String, Object>) arguments)
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	public void publishToAllExcept(Collection<Long> excludeWampSessionIds, String topic,
			@Nullable Collection<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.arguments(argumentsKw)
				.exclude(CollectionHelper.toSet(excludeWampSessionIds)).build());
	}

	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable T... arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addExclude(excludeWampSessionId).build());
	}

	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable Collection<T> arguments) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.addExclude(excludeWampSessionId).build());
	}

	public <T> void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable Map<String, T> arguments) {
		publish(createBuilder(topic).arguments((Map<String, Object>) arguments)
				.addExclude(excludeWampSessionId).build());
	}

	public void publishToAllExcept(long excludeWampSessionId, String topic,
			@Nullable Collection<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		publish(createBuilder(topic).arguments(CollectionHelper.toList(arguments))
				.arguments(argumentsKw).addExclude(excludeWampSessionId).build());
	}

	private Builder createBuilder(String topic) {
		long requestId = IdGenerator.newLinearId(this.atomicLong);
		return PublishMessage.builder(requestId, topic);
	}

}
