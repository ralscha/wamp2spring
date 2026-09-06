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
package ch.rasc.wamp2spring.servlet.longpoll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;

public class LongpollTransportRegistryTest {

	@Test
	public void createValidatesProtocolAndStoresTransport() {
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofSeconds(10));

		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);

		assertThat(transport.getTransportId()).isNotBlank();
		assertThat(registry.get(transport.getTransportId())).isSameAs(transport);
		assertThatThrownBy(() -> registry.create("wamp.2.invalid", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported longpoll protocol");
	}

	@Test
	public void removeIdleTransportsEvictsExpiredEntries() throws InterruptedException {
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofMillis(1));

		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		assertThat(registry.getTransports()).hasSize(1);
		TimeUnit.MILLISECONDS.sleep(5);

		registry.removeIdleTransports();

		assertThat(registry.remove(transport.getTransportId())).isNull();
		assertThat(registry.getTransports()).isEmpty();
	}

	@Test
	public void expiredReceiveDoesNotDiscardNextMessage() throws IOException {
		LongpollTransportRegistry registry = registry(new ArrayList<>(), 4);
		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		DeferredResult<ResponseEntity<byte[]>> expired = registry.receive(transport);
		expired.setResult(ResponseEntity.noContent().build());

		registry.queue(transport, new PublishedMessage(1L, 2L), false);

		assertThat(transport.getOutboundMessages()).hasSize(1);
		PublishedMessage message = WampMessage.deserialize(transport.getObjectMapper(),
				response(registry.receive(transport)).getBody());
		assertThat(message.getRequestId()).isEqualTo(1L);
	}

	@Test
	public void receiveAndPublishCanRaceWithoutStrandingMessages() throws Exception {
		LongpollTransportRegistry registry = registry(new ArrayList<>(), 4);
		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		var executor = Executors.newFixedThreadPool(2);
		try {
			for (long requestId = 1; requestId <= 50; requestId++) {
				CountDownLatch start = new CountDownLatch(1);
				PublishedMessage published = new PublishedMessage(requestId, 100L);
				var receiving = executor.submit(() -> {
					start.await();
					return registry.receive(transport);
				});
				var publishing = executor.submit(() -> {
					start.await();
					registry.queue(transport, published, false);
					return null;
				});
				start.countDown();
				publishing.get(5, TimeUnit.SECONDS);
				PublishedMessage delivered = WampMessage.deserialize(transport.getObjectMapper(),
						response(receiving.get(5, TimeUnit.SECONDS)).getBody());
				assertThat(delivered.getRequestId()).isEqualTo(requestId);
				assertThat(transport.getOutboundMessages()).isEmpty();
			}
		}
		finally {
			executor.shutdownNow();
			registry.stop();
		}
	}

	@Test
	public void overlappingReceivesConflictAndCloseCompletesPendingRequestOnce() {
		List<LongpollTransport> closed = new ArrayList<>();
		LongpollTransportRegistry registry = registry(closed, 4);
		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		var pending = registry.receive(transport);
		assertThat(response(registry.receive(transport)).getStatusCode().value()).isEqualTo(409);

		registry.remove(transport.getTransportId());
		registry.remove(transport.getTransportId());
		registry.stop();

		assertThat(response(pending).getStatusCode().value()).isEqualTo(204);
		assertThat(closed).containsExactly(transport);
		assertThat(response(registry.receive(transport)).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	public void serverGoodbyeDrainsInOrderAndNotifiesClosure() throws IOException {
		List<LongpollTransport> closed = new ArrayList<>();
		LongpollTransportRegistry registry = registry(closed, 4);
		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		registry.queue(transport, new PublishedMessage(1L, 2L), false);
		GoodbyeMessage goodbye = new GoodbyeMessage(WampError.GOODBYE_AND_OUT);
		goodbye.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, transport.getTransportId());
		registry.handleMessage(goodbye);
		registry.queue(transport, new PublishedMessage(3L, 4L), false);

		assertThat(WampMessage.<WampMessage>deserialize(transport.getObjectMapper(),
				response(registry.receive(transport)).getBody()))
			.isInstanceOf(PublishedMessage.class);
		assertThat(closed).isEmpty();
		assertThat(WampMessage.<WampMessage>deserialize(transport.getObjectMapper(),
				response(registry.receive(transport)).getBody()))
			.isInstanceOf(GoodbyeMessage.class);
		assertThat(closed).containsExactly(transport);
		assertThat(registry.getTransports()).isEmpty();
	}

	@Test
	public void overflowAndShutdownNotifyClosureAndReleaseBuffers() {
		List<LongpollTransport> closed = new ArrayList<>();
		LongpollTransportRegistry registry = registry(closed, 1);
		LongpollTransport overflowing = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		registry.queue(overflowing, new PublishedMessage(1L, 2L), false);
		registry.queue(overflowing, new PublishedMessage(3L, 4L), false);
		assertThat(closed).containsExactly(overflowing);
		assertThat(overflowing.getOutboundMessages()).isEmpty();

		LongpollTransport waiting = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		var pending = registry.receive(waiting);
		registry.stop();
		assertThat(response(pending).getStatusCode().value()).isEqualTo(204);
		assertThat(closed).containsExactly(overflowing, waiting);
	}

	@Test
	public void lifecycleAutomaticallyExpiresIdleTransports() throws InterruptedException {
		CountDownLatch closed = new CountDownLatch(1);
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofMillis(20), transport -> closed.countDown());
		registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		registry.start();
		try {
			assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(registry.getTransports()).isEmpty();
		}
		finally {
			registry.stop();
		}
		assertThat(registry.isRunning()).isFalse();
	}

	@Test
	public void pendingReceiveSurvivesIdleSweepButOutboundTrafficDoesNotKeepAbandonedTransportAlive()
			throws InterruptedException {
		List<LongpollTransport> closed = new ArrayList<>();
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofMillis(1), closed::add);
		LongpollTransport waiting = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		LongpollTransport abandoned = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		var pending = registry.receive(waiting);
		TimeUnit.MILLISECONDS.sleep(5);
		registry.queue(abandoned, new PublishedMessage(1L, 2L), false);

		registry.removeIdleTransports();

		assertThat(closed).containsExactly(abandoned);
		assertThat(pending.hasResult()).isFalse();
		registry.stop();
		assertThat(response(pending).getStatusCode().value()).isEqualTo(204);
	}

	private static LongpollTransportRegistry registry(List<LongpollTransport> closed, int maxQueueSize) {
		return new LongpollTransportRegistry(Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()),
				maxQueueSize, Duration.ofSeconds(5), Duration.ofSeconds(10), closed::add);
	}

	@SuppressWarnings("unchecked")
	private static ResponseEntity<byte[]> response(DeferredResult<ResponseEntity<byte[]>> result) {
		assertThat(result.hasResult()).isTrue();
		return (ResponseEntity<byte[]>) result.getResult();
	}

}
