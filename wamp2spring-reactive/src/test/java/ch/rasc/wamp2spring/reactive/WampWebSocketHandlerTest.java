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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.auth.WampAuthentication;
import ch.rasc.wamp2spring.auth.WampAuthenticationChallenge;
import ch.rasc.wamp2spring.auth.WampAuthenticationProvider;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.util.WampJson;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class WampWebSocketHandlerTest {

	@Test
	void remoteDisconnectUnsubscribesOutboundChannelAndPublishesDisconnect() {
		var outbound = new ExecutorSubscribableChannel();
		var events = new ArrayList<Object>();
		var handler = handler(outbound, events);
		var session = session(Mono.empty(), Flux.empty());
		session.getAttributes().put("wamp2spring.session.id", 42L);

		handler.handle(session).block(Duration.ofSeconds(2));

		assertThat(outbound.getSubscribers()).isEmpty();
		assertThat(events).hasSize(1);
		assertThat(events.get(0)).isInstanceOf(WampDisconnectEvent.class);
		assertThat(session.getAttributes()).doesNotContainKey("wamp2spring.session.id");
	}

	@Test
	void resolvesTransportPrincipalBeforeReceivingHello() {
		var outbound = new ExecutorSubscribableChannel();
		var events = new ArrayList<Object>();
		var handler = handler(outbound, events);
		var principal = Sinks.<Principal>one();
		var receiving = new AtomicBoolean();
		var hello = text("[1,{\"roles\":{\"caller\":{}}}]");
		var session = session(principal.asMono(),
				Flux.concat(Flux.just(hello), Flux.never()).doOnSubscribe(subscription -> receiving.set(true)));
		Principal user = () -> "authenticated-user";
		var subscription = handler.handle(session).subscribe();
		try {
			assertThat(receiving).isFalse();
			assertThat(events).isEmpty();
			assertThat(principal.tryEmitValue(user)).isEqualTo(Sinks.EmitResult.OK);
			assertThat(receiving).isTrue();
			assertThat(events).hasSize(1);
			assertThat(((WampSessionEstablishedEvent) events.get(0)).getPrincipal()).isSameAs(user);
		}
		finally {
			subscription.dispose();
		}
		assertThat(outbound.getSubscribers()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void goodbyeIsFlushedBeforeClosingTheSocket() {
		var outbound = new ExecutorSubscribableChannel();
		var handler = handler(outbound, new ArrayList<>());
		var session = session(Mono.empty(), Flux.never());
		var order = new ArrayList<String>();
		var flushed = Sinks.<Void>one();
		doAnswer(invocation -> Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0))
			.doOnNext(message -> order.add("sent"))
			.then(flushed.asMono())).when(session).send(any());
		when(session.close(any())).thenAnswer(invocation -> Mono.fromRunnable(() -> order.add("closed")));
		var subscription = handler.handle(session).subscribe();
		try {
			var goodbye = new GoodbyeMessage(WampError.GOODBYE_AND_OUT);
			goodbye.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
			outbound.send(goodbye);
			assertThat(order).containsExactly("sent");
			assertThat(flushed.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
			assertThat(order).containsExactly("sent", "closed");
			assertThat(outbound.getSubscribers()).isEmpty();
		}
		finally {
			subscription.dispose();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void failedWelcomeDoesNotReestablishADisconnectedSession() {
		var outbound = new ExecutorSubscribableChannel();
		var events = new ArrayList<Object>();
		var handler = handler(outbound, events);
		var hello = text("[1,{\"roles\":{\"caller\":{}}}]");
		var session = session(Mono.empty(), Flux.concat(Flux.just(hello), Flux.never()));
		doAnswer(invocation -> Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0))
			.concatMap(message -> Mono.error(new IllegalStateException("write failed")))
			.then()).when(session).send(any());

		assertThatThrownBy(() -> handler.handle(session).block(Duration.ofSeconds(2)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("write failed");

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(WampSessionEstablishedEvent.class);
		assertThat(events.get(1)).isInstanceOf(WampDisconnectEvent.class);
		assertThat(outbound.getSubscribers()).isEmpty();
	}

	@Test
	void cancellationWaitsForAuthenticationBeforeCleaningUpTheSession() throws Exception {
		var authenticating = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var cancelling = new CountDownLatch(1);
		var provider = mock(WampAuthenticationProvider.class);
		when(provider.getAuthMethod()).thenReturn("ticket");
		when(provider.challenge(any())).thenReturn(new WampAuthenticationChallenge(Map.of()));
		when(provider.authenticate(any(), any(), any())).thenAnswer(invocation -> {
			authenticating.countDown();
			assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
			return new WampAuthentication("user", "member");
		});
		var events = new CopyOnWriteArrayList<Object>();
		var outbound = new ExecutorSubscribableChannel();
		var handler = handler(outbound, events, provider);
		var hello = text("[1,{\"roles\":{\"caller\":{}},\"authmethods\":[\"ticket\"]}]");
		var authenticate = text("[5,\"ticket\",{}]");
		var session = session(Mono.empty(), Flux.concat(Flux.just(hello, authenticate), Flux.never()));
		var subscriber = new BaseSubscriber<Void>() {
		};
		var executor = Executors.newFixedThreadPool(2);
		try {
			var receive = executor.submit(() -> handler.handle(session).subscribe(subscriber));
			assertThat(authenticating.await(5, TimeUnit.SECONDS)).isTrue();
			var cancel = executor.submit(() -> {
				cancelling.countDown();
				subscriber.dispose();
			});
			assertThat(cancelling.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> cancel.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			proceed.countDown();
			receive.get(5, TimeUnit.SECONDS);
			cancel.get(5, TimeUnit.SECONDS);
			assertThat(events).hasSize(2);
			assertThat(events.get(0)).isInstanceOf(WampSessionEstablishedEvent.class);
			assertThat(events.get(1)).isInstanceOf(WampDisconnectEvent.class);
			assertThat(session.getAttributes()).doesNotContainKeys("wamp2spring.pending.auth",
					"wamp2spring.session.id");
			assertThat(outbound.getSubscribers()).isEmpty();
		}
		finally {
			proceed.countDown();
			executor.shutdownNow();
		}
	}

	private static WampWebSocketHandler handler(ExecutorSubscribableChannel outbound, List<Object> events,
			WampAuthenticationProvider... authenticationProviders) {
		var mapper = WampJson.createJsonObjectMapper();
		var handler = new WampWebSocketHandler(mapper, mapper, mapper, mapper, outbound,
				new ExecutorSubscribableChannel(), new Features(), List.of(authenticationProviders));
		handler.setApplicationEventPublisher(events::add);
		handler.start();
		return handler;
	}

	@SuppressWarnings("unchecked")
	private static WebSocketSession session(Mono<Principal> principal, Flux<WebSocketMessage> input) {
		var session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("socket");
		when(session.getAttributes()).thenReturn(new HashMap<>());
		when(session.getHandshakeInfo()).thenReturn(new HandshakeInfo(URI.create("http://localhost/wamp"),
				new HttpHeaders(), principal, WampWebSocketHandler.JSON_PROTOCOL));
		when(session.receive()).thenReturn(input);
		doAnswer(invocation -> Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0)).then()).when(session)
			.send(any());
		when(session.close(any())).thenReturn(Mono.empty());
		when(session.textMessage(anyString())).thenAnswer(invocation -> text(invocation.getArgument(0)));
		return session;
	}

	private static WebSocketMessage text(String value) {
		return new WebSocketMessage(WebSocketMessage.Type.TEXT,
				DefaultDataBufferFactory.sharedInstance.wrap(value.getBytes(StandardCharsets.UTF_8)));
	}

}
