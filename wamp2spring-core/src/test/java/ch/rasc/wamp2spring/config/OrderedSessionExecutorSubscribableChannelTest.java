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
package ch.rasc.wamp2spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import ch.rasc.wamp2spring.message.WampMessageHeader;

public class OrderedSessionExecutorSubscribableChannelTest {

	@Test
	public void sameSessionMessagesAreHandledSequentially() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			OrderedSessionExecutorSubscribableChannel channel = new OrderedSessionExecutorSubscribableChannel(executor);
			CountDownLatch firstStarted = new CountDownLatch(1);
			CountDownLatch releaseFirst = new CountDownLatch(1);
			CountDownLatch secondStarted = new CountDownLatch(1);
			CountDownLatch completed = new CountDownLatch(2);
			AtomicInteger concurrentHandlers = new AtomicInteger();
			AtomicInteger maxConcurrentHandlers = new AtomicInteger();
			List<Integer> handledPayloads = new CopyOnWriteArrayList<>();

			channel.subscribe(message -> {
				int currentConcurrent = concurrentHandlers.incrementAndGet();
				maxConcurrentHandlers.accumulateAndGet(currentConcurrent, Math::max);
				try {
					Integer payload = (Integer) message.getPayload();
					if (payload == 1) {
						firstStarted.countDown();
						assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue();
					}
					else if (payload == 2) {
						secondStarted.countDown();
					}
					handledPayloads.add(payload);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new AssertionError(ex);
				}
				finally {
					concurrentHandlers.decrementAndGet();
					completed.countDown();
				}
			});

			channel.send(MessageBuilder.withPayload(1).setHeader(WampMessageHeader.WAMP_SESSION_ID.name(), 1L).build());
			assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();

			channel.send(MessageBuilder.withPayload(2).setHeader(WampMessageHeader.WAMP_SESSION_ID.name(), 1L).build());

			assertThat(secondStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();
			releaseFirst.countDown();
			assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(handledPayloads).containsExactly(1, 2);
			assertThat(maxConcurrentHandlers.get()).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void differentSessionsCanProceedIndependently() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			OrderedSessionExecutorSubscribableChannel channel = new OrderedSessionExecutorSubscribableChannel(executor);
			CountDownLatch firstStarted = new CountDownLatch(1);
			CountDownLatch releaseFirst = new CountDownLatch(1);
			CountDownLatch otherSessionStarted = new CountDownLatch(1);
			CountDownLatch completed = new CountDownLatch(2);

			channel.subscribe(message -> {
				try {
					Long sessionId = (Long) message.getHeaders().get(WampMessageHeader.WAMP_SESSION_ID.name());
					if (Long.valueOf(1L).equals(sessionId)) {
						firstStarted.countDown();
						assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue();
					}
					else if (Long.valueOf(2L).equals(sessionId)) {
						otherSessionStarted.countDown();
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new AssertionError(ex);
				}
				finally {
					completed.countDown();
				}
			});

			channel.send(MessageBuilder.withPayload("first")
				.setHeader(WampMessageHeader.WAMP_SESSION_ID.name(), 1L)
				.build());
			assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();

			channel.send(MessageBuilder.withPayload("other")
				.setHeader(WampMessageHeader.WAMP_SESSION_ID.name(), 2L)
				.build());

			assertThat(otherSessionStarted.await(2, TimeUnit.SECONDS)).isTrue();
			releaseFirst.countDown();
			assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			executor.shutdownNow();
		}
	}

}