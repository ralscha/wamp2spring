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

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.message.WampMessage;

public final class LongpollTransport {

	private final String transportId;

	private final String protocol;

	private final ObjectMapper objectMapper;

	private final LinkedBlockingDeque<WampMessage> outboundMessages;

	@Nullable private final Principal principal;

	private final long createdAt;

	private final AtomicLong lastActivity = new AtomicLong();

	private final AtomicReference<DeferredResult<ResponseEntity<byte[]>>> pendingReceive = new AtomicReference<>();

	private final Map<String, Object> attributes = new ConcurrentHashMap<>();

	private final AtomicBoolean closeAfterDrain = new AtomicBoolean();

	LongpollTransport(String transportId, String protocol, ObjectMapper objectMapper, @Nullable Principal principal,
			int maxQueueSize) {
		this.transportId = transportId;
		this.protocol = protocol;
		this.objectMapper = objectMapper;
		this.principal = principal;
		this.outboundMessages = new LinkedBlockingDeque<>(maxQueueSize);
		this.createdAt = System.currentTimeMillis();
		this.lastActivity.set(this.createdAt);
	}

	public String getTransportId() {
		return this.transportId;
	}

	public String getProtocol() {
		return this.protocol;
	}

	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	public LinkedBlockingDeque<WampMessage> getOutboundMessages() {
		return this.outboundMessages;
	}

	public @Nullable Principal getPrincipal() {
		return this.principal;
	}

	public long getCreatedAt() {
		return this.createdAt;
	}

	public long getLastActivity() {
		return this.lastActivity.get();
	}

	public void touch() {
		this.lastActivity.set(System.currentTimeMillis());
	}

	public AtomicReference<DeferredResult<ResponseEntity<byte[]>>> getPendingReceive() {
		return this.pendingReceive;
	}

	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

	public AtomicBoolean getCloseAfterDrain() {
		return this.closeAfterDrain;
	}

}