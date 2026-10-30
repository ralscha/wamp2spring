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
package ch.rasc.wamp2spring.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.GenericMessage;

import reactor.core.Exceptions;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

class MessageChannelReactiveUtilsTest {

	@Test
	void respectsDemandAndUnsubscribesOnOverflow() {
		var channel = new ExecutorSubscribableChannel();
		var received = new ArrayList<Message<String>>();
		var failure = new AtomicReference<Throwable>();
		var subscriber = new BaseSubscriber<Message<String>>() {
			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				request(1);
			}

			@Override
			protected void hookOnNext(Message<String> message) {
				received.add(message);
			}

			@Override
			protected void hookOnError(Throwable throwable) {
				failure.set(throwable);
			}
		};
		MessageChannelReactiveUtils.<String>toPublisher(channel).subscribe(subscriber);
		channel.send(new GenericMessage<>("first"));
		channel.send(new GenericMessage<>("second"));

		assertThat(received).extracting(Message::getPayload).containsExactly("first");
		assertThat(Exceptions.isOverflow(failure.get())).isTrue();
		assertThat(channel.getSubscribers()).isEmpty();
	}

	@Test
	void unrelatedMessagesDoNotOverflowASubscriberWithoutDemand() {
		var channel = new ExecutorSubscribableChannel();
		var received = new ArrayList<Message<String>>();
		var failure = new AtomicReference<Throwable>();
		var subscriber = new BaseSubscriber<Message<String>>() {
			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				// Deliberately wait before requesting this session's messages.
			}

			@Override
			protected void hookOnNext(Message<String> message) {
				received.add(message);
			}

			@Override
			protected void hookOnError(Throwable throwable) {
				failure.set(throwable);
			}
		};
		MessageChannelReactiveUtils.<String>toPublisher(channel, message -> "mine".equals(message.getPayload()))
			.subscribe(subscriber);
		try {
			channel.send(new GenericMessage<>("another session"));
			assertThat(failure.get()).isNull();
			assertThat(received).isEmpty();
			subscriber.request(1);
			channel.send(new GenericMessage<>("mine"));
			assertThat(received).extracting(Message::getPayload).containsExactly("mine");
		}
		finally {
			subscriber.dispose();
		}
	}

	@Test
	void cancellationUnsubscribesTheChannelHandler() {
		var channel = new ExecutorSubscribableChannel();
		var subscription = Flux.from(MessageChannelReactiveUtils.toPublisher(channel)).subscribe();
		assertThat(channel.getSubscribers()).hasSize(1);
		subscription.dispose();
		assertThat(channel.getSubscribers()).isEmpty();
	}

}
