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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.web.context.request.async.DeferredResult;

import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.InternalCloseMessage;

public class LongpollTransportRegistry implements SmartLifecycle, MessageHandler {

	private static final Log logger = LogFactory.getLog(LongpollTransportRegistry.class);

	private final Map<String, ObjectMapper> objectMappers;

	private final Map<String, LongpollTransport> transports = new ConcurrentHashMap<>();

	private final SecureRandom secureRandom = new SecureRandom();

	private final int maxQueueSize;

	private final Duration receiveTimeout;

	private final Duration transportIdleTimeout;

	private volatile boolean running;

	private final Consumer<LongpollTransport> closeListener;

	@Nullable private ThreadPoolTaskScheduler cleanupScheduler;

	@Nullable private ScheduledFuture<?> cleanupTask;

	public LongpollTransportRegistry(Map<String, ObjectMapper> objectMappers, int maxQueueSize, Duration receiveTimeout,
			Duration transportIdleTimeout) {
		this(objectMappers, maxQueueSize, receiveTimeout, transportIdleTimeout, transport -> {
		});
	}

	public LongpollTransportRegistry(Map<String, ObjectMapper> objectMappers, int maxQueueSize, Duration receiveTimeout,
			Duration transportIdleTimeout, Consumer<LongpollTransport> closeListener) {
		Assert.notEmpty(objectMappers, "objectMappers must not be empty");
		Assert.isTrue(maxQueueSize > 0, "maxQueueSize must be greater than zero");
		Assert.isTrue(receiveTimeout.toMillis() > 0, "receiveTimeout must be at least one millisecond");
		Assert.isTrue(transportIdleTimeout.toMillis() > 0, "transportIdleTimeout must be at least one millisecond");
		this.objectMappers = Map.copyOf(objectMappers);
		this.maxQueueSize = maxQueueSize;
		this.receiveTimeout = receiveTimeout;
		this.transportIdleTimeout = transportIdleTimeout;
		this.closeListener = closeListener;
	}

	public LongpollTransport create(String protocol, @Nullable Principal principal) {
		ObjectMapper objectMapper = resolveObjectMapper(protocol);
		String transportId = newTransportId();
		LongpollTransport transport = new LongpollTransport(transportId, protocol, objectMapper, principal,
				this.maxQueueSize);
		this.transports.put(transportId, transport);
		return transport;
	}

	public @Nullable LongpollTransport get(String transportId) {
		LongpollTransport transport = this.transports.get(transportId);
		if (transport != null) {
			synchronized (transport) {
				if (this.transports.get(transportId) != transport) {
					return null;
				}
				transport.touch();
			}
		}
		return transport;
	}

	public @Nullable LongpollTransport remove(String transportId) {
		LongpollTransport transport = this.transports.get(transportId);
		return transport != null && remove(transport, null) ? transport : null;
	}

	public Collection<LongpollTransport> getTransports() {
		return List.copyOf(this.transports.values());
	}

	public Duration getReceiveTimeout() {
		return this.receiveTimeout;
	}

	public Duration getTransportIdleTimeout() {
		return this.transportIdleTimeout;
	}

	public void removeIdleTransports() {
		long cutoff = System.currentTimeMillis() - this.transportIdleTimeout.toMillis();
		for (LongpollTransport transport : this.transports.values()) {
			remove(transport, cutoff);
		}
	}

	public void queue(LongpollTransport transport, WampMessage wampMessage, boolean closeAfterDrain) {
		boolean close;
		synchronized (transport) {
			if (this.transports.get(transport.getTransportId()) != transport || transport.getCloseAfterDrain().get()) {
				return;
			}
			transport.getCloseAfterDrain().set(closeAfterDrain);
			close = !transport.getOutboundMessages().offer(wampMessage);
			if (close) {
				transport.getCloseAfterDrain().set(true);
			}
			if (!close) {
				deliverPendingReceive(transport);
				close = closeAfterDrain && transport.getOutboundMessages().isEmpty();
			}
		}
		if (close) {
			remove(transport.getTransportId());
		}
	}

	public DeferredResult<ResponseEntity<byte[]>> receive(LongpollTransport transport) {
		DeferredResult<ResponseEntity<byte[]>> result = new DeferredResult<>(this.receiveTimeout.toMillis(),
				ResponseEntity.noContent().build());
		boolean close;
		synchronized (transport) {
			if (this.transports.get(transport.getTransportId()) != transport) {
				result.setResult(ResponseEntity.notFound().build());
				return result;
			}
			DeferredResult<ResponseEntity<byte[]>> pending = transport.getPendingReceive().get();
			if (pending != null && !pending.isSetOrExpired()) {
				result.setResult(ResponseEntity.status(HttpStatus.CONFLICT)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"error\":\"receive already pending for transport\"}".getBytes(StandardCharsets.UTF_8)));
				return result;
			}
			result.onCompletion(() -> transport.getPendingReceive().compareAndSet(result, null));
			result.onTimeout(() -> transport.getPendingReceive().compareAndSet(result, null));
			transport.touch();
			transport.getPendingReceive().set(result);
			deliverPendingReceive(transport);
			close = transport.getCloseAfterDrain().get() && transport.getOutboundMessages().isEmpty();
		}
		if (close) {
			remove(transport.getTransportId());
		}
		return result;
	}

	public ResponseEntity<byte[]> dequeueResponse(LongpollTransport transport) {
		ResponseEntity<byte[]> response;
		boolean close;
		synchronized (transport) {
			WampMessage nextMessage = transport.getOutboundMessages().peek();
			if (nextMessage == null) {
				return ResponseEntity.noContent().build();
			}
			response = serializeResponse(transport, nextMessage);
			transport.getOutboundMessages().poll();
			close = transport.getCloseAfterDrain().get() && transport.getOutboundMessages().isEmpty();
		}
		if (close) {
			remove(transport.getTransportId());
		}
		return response;
	}

	private static void deliverPendingReceive(LongpollTransport transport) {
		WampMessage message = transport.getOutboundMessages().peek();
		if (message == null) {
			return;
		}
		DeferredResult<ResponseEntity<byte[]>> pending = transport.getPendingReceive().getAndSet(null);
		if (pending != null && pending.setResult(serializeResponse(transport, message))) {
			transport.getOutboundMessages().poll();
			transport.touch();
		}
	}

	private boolean remove(LongpollTransport transport, @Nullable Long idleCutoff) {
		DeferredResult<ResponseEntity<byte[]>> pending;
		synchronized (transport) {
			pending = transport.getPendingReceive().get();
			if (idleCutoff != null
					&& (transport.getLastActivity() >= idleCutoff || (pending != null && !pending.isSetOrExpired()))) {
				return false;
			}
			if (!this.transports.remove(transport.getTransportId(), transport)) {
				return false;
			}
			transport.getCloseAfterDrain().set(true);
			transport.getPendingReceive().set(null);
			transport.getOutboundMessages().clear();
		}
		if (pending != null) {
			pending.setResult(ResponseEntity.noContent().build());
		}
		try {
			this.closeListener.accept(transport);
		}
		catch (RuntimeException ex) {
			logger.error("Failed to notify longpoll session closure", ex);
		}
		return true;
	}

	public ObjectMapper resolveObjectMapper(String protocol) {
		ObjectMapper objectMapper = this.objectMappers.get(protocol);
		if (objectMapper == null) {
			throw new IllegalArgumentException("Unsupported longpoll protocol: " + protocol);
		}
		return objectMapper;
	}

	public boolean supportsProtocol(String protocol) {
		return this.objectMappers.containsKey(protocol);
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		if (!(message instanceof WampMessage wampMessage)) {
			return;
		}
		String transportId = (String) message.getHeaders().get(WampMessageHeader.WEBSOCKET_SESSION_ID.name());
		if (transportId == null) {
			return;
		}
		LongpollTransport transport = this.transports.get(transportId);
		if (transport != null) {
			if (wampMessage instanceof InternalCloseMessage) {
				remove(transportId);
			}
			else {
				queue(transport, wampMessage,
						wampMessage instanceof GoodbyeMessage || wampMessage instanceof AbortMessage);
			}
		}
	}

	@Override
	public synchronized void start() {
		if (this.running) {
			return;
		}
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix("wamp-longpoll-cleanup-");
		scheduler.setDaemon(true);
		scheduler.initialize();
		this.cleanupScheduler = scheduler;
		this.cleanupTask = scheduler.scheduleWithFixedDelay(this::removeIdleTransports, this.transportIdleTimeout);
		this.running = true;
	}

	@Override
	public synchronized void stop() {
		this.running = false;
		if (this.cleanupTask != null) {
			this.cleanupTask.cancel(false);
			this.cleanupTask = null;
		}
		if (this.cleanupScheduler != null) {
			this.cleanupScheduler.shutdown();
			this.cleanupScheduler = null;
		}
		for (LongpollTransport transport : getTransports()) {
			remove(transport.getTransportId());
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	private String newTransportId() {
		byte[] bytes = new byte[18];
		String transportId;
		do {
			this.secureRandom.nextBytes(bytes);
			transportId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}
		while (this.transports.containsKey(transportId));
		return transportId;
	}

	private static ResponseEntity<byte[]> serializeResponse(LongpollTransport transport, WampMessage wampMessage) {
		try {
			return ResponseEntity.ok()
				.contentType(LongpollMessageCodec.mediaType(transport.getProtocol()))
				.body(LongpollMessageCodec.serialize(transport.getProtocol(), transport.getObjectMapper(),
						wampMessage));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to serialize longpoll message", ex);
		}
	}

}
