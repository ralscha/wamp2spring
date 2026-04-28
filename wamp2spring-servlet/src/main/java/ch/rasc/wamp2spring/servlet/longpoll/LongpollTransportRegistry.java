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
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.http.ResponseEntity;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;
import org.springframework.web.context.request.async.DeferredResult;

import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;

public class LongpollTransportRegistry implements SmartLifecycle, MessageHandler {

	private final Map<String, ObjectMapper> objectMappers;

	private final Map<String, LongpollTransport> transports = new ConcurrentHashMap<>();

	private final SecureRandom secureRandom = new SecureRandom();

	private final int maxQueueSize;

	private final Duration receiveTimeout;

	private final Duration transportIdleTimeout;

	private volatile boolean running;

	public LongpollTransportRegistry(Map<String, ObjectMapper> objectMappers, int maxQueueSize, Duration receiveTimeout,
			Duration transportIdleTimeout) {
		Assert.notEmpty(objectMappers, "objectMappers must not be empty");
		Assert.isTrue(maxQueueSize > 0, "maxQueueSize must be greater than zero");
		Assert.isTrue(!receiveTimeout.isNegative() && !receiveTimeout.isZero(),
				"receiveTimeout must be greater than zero");
		Assert.isTrue(!transportIdleTimeout.isNegative() && !transportIdleTimeout.isZero(),
				"transportIdleTimeout must be greater than zero");
		this.objectMappers = Map.copyOf(objectMappers);
		this.maxQueueSize = maxQueueSize;
		this.receiveTimeout = receiveTimeout;
		this.transportIdleTimeout = transportIdleTimeout;
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
			transport.touch();
		}
		return transport;
	}

	public @Nullable LongpollTransport remove(String transportId) {
		return this.transports.remove(transportId);
	}

	public Collection<LongpollTransport> getTransports() {
		return this.transports.values();
	}

	public Duration getReceiveTimeout() {
		return this.receiveTimeout;
	}

	public Duration getTransportIdleTimeout() {
		return this.transportIdleTimeout;
	}

	public void removeIdleTransports() {
		long cutoff = System.currentTimeMillis() - this.transportIdleTimeout.toMillis();
		this.transports.entrySet().removeIf(entry -> entry.getValue().getLastActivity() < cutoff);
	}

	public void queue(LongpollTransport transport, WampMessage wampMessage, boolean closeAfterDrain) {
		transport.touch();
		if (closeAfterDrain) {
			transport.getCloseAfterDrain().set(true);
		}

		DeferredResult<ResponseEntity<byte[]>> pendingReceive = transport.getPendingReceive().getAndSet(null);
		if (pendingReceive != null) {
			pendingReceive.setResult(serializeResponse(transport, wampMessage));
			if (transport.getCloseAfterDrain().get()) {
				this.transports.remove(transport.getTransportId(), transport);
			}
			return;
		}

		if (!transport.getOutboundMessages().offer(wampMessage)) {
			transport.getCloseAfterDrain().set(true);
			this.transports.remove(transport.getTransportId(), transport);
		}
	}

	public ResponseEntity<byte[]> dequeueResponse(LongpollTransport transport) {
		WampMessage nextMessage = transport.getOutboundMessages().poll();
		if (nextMessage == null) {
			return ResponseEntity.noContent().build();
		}
		ResponseEntity<byte[]> response = serializeResponse(transport, nextMessage);
		if (transport.getCloseAfterDrain().get() && transport.getOutboundMessages().isEmpty()) {
			this.transports.remove(transport.getTransportId(), transport);
		}
		return response;
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
			queue(transport, wampMessage, false);
		}
	}

	@Override
	public void start() {
		this.running = true;
	}

	@Override
	public void stop() {
		this.running = false;
		this.transports.clear();
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