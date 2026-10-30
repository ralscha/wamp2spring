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

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.servlet.WampSessionSupport;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;
import ch.rasc.wamp2spring.util.WampJson;

class LongpollSessionConcurrencyTest {

	@Test
	void closeCannotLeaveASessionEstablishedAfterItsTransportWasRemoved() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var closing = new CountDownLatch(1);
		var events = new CopyOnWriteArrayList<Object>();
		var support = new WampSessionSupport(new Features(), List.of(), events::add) {
			@Override
			public void populateHeaders(String sessionId, @Nullable Principal principal, Map<String, Object> attributes,
					WampMessage message) {
				super.populateHeaders(sessionId, principal, attributes, message);
				entered.countDown();
				try {
					assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
			}
		};
		var mapper = WampJson.createJsonObjectMapper();
		var registry = new LongpollTransportRegistry(Map.of(WampSubProtocolHandler.JSON_PROTOCOL, mapper), 4,
				Duration.ofSeconds(5), Duration.ofSeconds(10),
				transport -> support.afterSessionEnded(transport.getTransportId(), transport.getPrincipal(),
						transport.getAttributes()));
		var transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		var controller = new WampLongpollController(registry, new ExecutorSubscribableChannel(), support);
		var payload = LongpollMessageCodec.serialize(transport.getProtocol(), mapper,
				new HelloMessage(List.of(new WampRole("caller"))));
		var executor = Executors.newFixedThreadPool(2);
		try {
			var send = executor.submit(() -> controller.send(transport.getTransportId(), payload));
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			var close = executor.submit(() -> {
				closing.countDown();
				return controller.close(transport.getTransportId());
			});
			assertThat(closing.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> close.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			proceed.countDown();
			assertThat(send.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(204);
			assertThat(close.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(204);
			assertThat(registry.getTransports()).isEmpty();
			assertThat(events).hasSize(2);
			assertThat(events.get(0)).isInstanceOf(WampSessionEstablishedEvent.class);
			assertThat(events.get(1)).isInstanceOf(WampDisconnectEvent.class);
		}
		finally {
			proceed.countDown();
			executor.shutdownNow();
			registry.stop();
		}
	}

}
