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
package ch.rasc.wamp2spring.session.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;

class SessionRepositoryMessageInterceptorTest {

	@Test
	void refreshesSessionFromWebSocketAttributes() {
		var repository = new MapSessionRepository(new ConcurrentHashMap<>());
		var session = repository.createSession();
		var previousAccess = Instant.now().minusSeconds(30);
		session.setLastAccessedTime(previousAccess);
		repository.save(session);
		var interceptor = new SessionRepositoryMessageInterceptor<>(repository);
		var message = PublishMessage.builder(1, "topic").build();
		message.setHeader(WampMessageHeader.SESSION_ATTRIBUTES, Map.of("SPRING.SESSION.ID", session.getId()));

		assertSame(message, interceptor.preSend(message, new ExecutorSubscribableChannel()));

		var refreshed = repository.findById(session.getId());
		assertNotNull(refreshed);
		assertTrue(refreshed.getLastAccessedTime().isAfter(previousAccess));
	}

	@Test
	void preservesLegacyHeadersAndDoesNotRefreshControlMessages() {
		var repository = new MapSessionRepository(new ConcurrentHashMap<>());
		var session = repository.createSession();
		var previousAccess = Instant.now().minusSeconds(30);
		session.setLastAccessedTime(previousAccess);
		repository.save(session);
		var interceptor = new SessionRepositoryMessageInterceptor<>(repository);
		var control = new HelloMessage(List.of());
		control.setHeader(WampMessageHeader.SESSION_ATTRIBUTES, Map.of("SPRING.SESSION.ID", session.getId()));
		assertSame(control, interceptor.preSend(control, new ExecutorSubscribableChannel()));
		var unchanged = repository.findById(session.getId());
		assertNotNull(unchanged);
		assertEquals(previousAccess, unchanged.getLastAccessedTime());

		var legacy = new GenericMessage<>("payload", Map.of("SPRING.SESSION.ID", session.getId()));
		assertSame(legacy, interceptor.preSend(legacy, new ExecutorSubscribableChannel()));
		var refreshed = repository.findById(session.getId());
		assertNotNull(refreshed);
		assertTrue(refreshed.getLastAccessedTime().isAfter(previousAccess));
	}

	@Test
	void retainsLegacySessionIdWhenTransportAttributesDoNotSupplyOne() {
		var repository = new MapSessionRepository(new ConcurrentHashMap<>());
		var session = repository.createSession();
		var previousAccess = Instant.now().minusSeconds(30);
		var interceptor = new SessionRepositoryMessageInterceptor<>(repository);
		for (var attributes : List.of(Map.of(), Map.of("SPRING.SESSION.ID", 42))) {
			session.setLastAccessedTime(previousAccess);
			repository.save(session);
			var message = new GenericMessage<>("payload", Map.of("SPRING.SESSION.ID", session.getId(),
					WampMessageHeader.SESSION_ATTRIBUTES.name(), attributes));

			assertSame(message, interceptor.preSend(message, new ExecutorSubscribableChannel()));

			var refreshed = repository.findById(session.getId());
			assertNotNull(refreshed);
			assertTrue(refreshed.getLastAccessedTime().isAfter(previousAccess));
		}
	}

	@Test
	void ignoresMissingSessionsAndMalformedAttributes() {
		var repository = new MapSessionRepository(new ConcurrentHashMap<>());
		var interceptor = new SessionRepositoryMessageInterceptor<MapSession>(repository);
		for (var attributes : List.of(Map.of(), Map.of("SPRING.SESSION.ID", "missing"),
				Map.of("SPRING.SESSION.ID", 42))) {
			var message = PublishMessage.builder(1, "topic").build();
			message.setHeader(WampMessageHeader.SESSION_ATTRIBUTES, attributes);
			assertSame(message, interceptor.preSend(message, new ExecutorSubscribableChannel()));
		}
	}

}
