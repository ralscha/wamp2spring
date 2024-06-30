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
package ch.rasc.wamp2spring.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketSession;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.RegisteredMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.CompletableFutureWebSocketHandler;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = EventsTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class EventsTest extends BaseWampTest {

	@Autowired
	private EventsBean eventsBean;

	@BeforeEach
	public void setup() {
		this.eventsBean.resetCounter();
	}

	@Test
	public void testSessionEstablishedEvent() throws Exception {

		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();

		try (WebSocketSession wsSession = startWebSocketSession(result,
				DataFormat.CBOR)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(DataFormat.CBOR, wsSession, helloMessage);
			WelcomeMessage welcomeMessage = result.getWelcomeMessage();

			result.waitAFewSeconds();

			assertThat(this.eventsBean.getMethodCounter())
					.containsOnlyKeys("sessionEstablished");

			List<WampEvent> events = this.eventsBean.getMethodCounter()
					.get("sessionEstablished");
			assertThat(events).hasSize(1);
			WampEvent event = events.get(0);
			assertThat(event).isInstanceOf(WampSessionEstablishedEvent.class);
			WampSessionEstablishedEvent wampEvent = (WampSessionEstablishedEvent) event;

			assertThat(wampEvent.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent.getWebSocketSessionId()).isNotNull();
		}
	}

	@Test
	public void testSessionDisconnectedEvent() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();

		WelcomeMessage welcomeMessage;
		try (WebSocketSession wsSession = startWebSocketSession(result,
				DataFormat.MSGPACK)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(DataFormat.MSGPACK, wsSession, helloMessage);
			welcomeMessage = result.getWelcomeMessage();
		}

		result.waitAFewSeconds();

		assertThat(this.eventsBean.getMethodCounter())
				.containsOnlyKeys("sessionEstablished", "disconnected");

		List<WampEvent> events = this.eventsBean.getMethodCounter()
				.get("sessionEstablished");
		assertThat(events).hasSize(1);
		WampEvent event = events.get(0);
		assertThat(event).isInstanceOf(WampSessionEstablishedEvent.class);
		WampSessionEstablishedEvent wampEvent1 = (WampSessionEstablishedEvent) event;
		assertThat(wampEvent1.getWampSessionId())
				.isEqualTo(welcomeMessage.getSessionId());
		assertThat(wampEvent1.getWebSocketSessionId()).isNotNull();

		events = this.eventsBean.getMethodCounter().get("disconnected");
		assertThat(events).hasSize(1);
		event = events.get(0);
		assertThat(event).isInstanceOf(WampDisconnectEvent.class);
		WampDisconnectEvent wampEvent2 = (WampDisconnectEvent) event;
		assertThat(wampEvent2.getWampSessionId())
				.isEqualTo(welcomeMessage.getSessionId());
		assertThat(wampEvent2.getWebSocketSessionId()).isNotNull();
	}

	@Test
	public void testProcedureEvents() throws Exception {

		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();

		try (WebSocketSession wsSession = startWebSocketSession(result,
				DataFormat.JSON)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(DataFormat.JSON, wsSession, helloMessage);
			WelcomeMessage welcomeMessage = result.getWelcomeMessage();

			RegisterMessage registerMessage = new RegisterMessage(1, "procedure");
			sendMessage(DataFormat.JSON, wsSession, registerMessage);
			RegisteredMessage registeredMessage = (RegisteredMessage) result
					.getWampMessage();

			result.waitAFewSeconds();
			assertThat(this.eventsBean.getMethodCounter())
					.containsOnlyKeys("sessionEstablished", "procedureRegistered");

			List<WampEvent> events = this.eventsBean.getMethodCounter()
					.get("sessionEstablished");
			assertThat(events).hasSize(1);
			WampEvent event = events.get(0);
			assertThat(event).isInstanceOf(WampSessionEstablishedEvent.class);
			WampSessionEstablishedEvent wampEvent1 = (WampSessionEstablishedEvent) event;
			assertThat(wampEvent1.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent1.getWebSocketSessionId()).isNotNull();

			events = this.eventsBean.getMethodCounter().get("procedureRegistered");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampProcedureRegisteredEvent.class);
			WampProcedureRegisteredEvent wampEvent2 = (WampProcedureRegisteredEvent) event;

			assertThat(wampEvent2.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent2.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent2.getProcedure()).isEqualTo("procedure");
			assertThat(wampEvent2.getRegistrationId())
					.isEqualTo(registeredMessage.getRegistrationId());

			this.eventsBean.resetCounter();

			UnregisterMessage unregisterMessage = new UnregisterMessage(33,
					registeredMessage.getRegistrationId());
			sendMessage(DataFormat.JSON, wsSession, unregisterMessage);
			result.reset();

			result.getWampMessage();
			result.waitAFewSeconds();
			assertThat(this.eventsBean.getMethodCounter())
					.containsOnlyKeys("procedureUnregistered");
			events = this.eventsBean.getMethodCounter().get("procedureUnregistered");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampProcedureUnregisteredEvent.class);
			WampProcedureUnregisteredEvent wampEvent3 = (WampProcedureUnregisteredEvent) event;

			assertThat(wampEvent3.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent3.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent3.getProcedure()).isEqualTo("procedure");
			assertThat(wampEvent3.getRegistrationId())
					.isEqualTo(registeredMessage.getRegistrationId());
		}
	}

	@Test
	public void testSubscriptionEvents() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();

		try (WebSocketSession wsSession = startWebSocketSession(result,
				DataFormat.JSON)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(DataFormat.JSON, wsSession, helloMessage);
			WelcomeMessage welcomeMessage = result.getWelcomeMessage();

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			sendMessage(DataFormat.JSON, wsSession, subscribeMessage);
			SubscribedMessage subscribedMessage = (SubscribedMessage) result
					.getWampMessage();

			result.waitAFewSeconds();
			assertThat(this.eventsBean.getMethodCounter()).containsOnlyKeys(
					"sessionEstablished", "subscriptionCreated", "subscribed");

			List<WampEvent> events = this.eventsBean.getMethodCounter()
					.get("subscriptionCreated");
			assertThat(events).hasSize(1);
			WampEvent event = events.get(0);
			assertThat(event).isInstanceOf(WampSubscriptionCreatedEvent.class);
			WampSubscriptionCreatedEvent wampEvent1 = (WampSubscriptionCreatedEvent) event;
			assertThat(wampEvent1.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent1.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent1.getSubscriptionDetail().getCreatedTimeMillis())
					.isGreaterThan(0);
			assertThat(wampEvent1.getSubscriptionDetail().getMatchPolicy())
					.isEqualTo(MatchPolicy.EXACT);
			assertThat(wampEvent1.getSubscriptionDetail().getTopic()).isEqualTo("topic");

			events = this.eventsBean.getMethodCounter().get("subscribed");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampSubscriptionSubscribedEvent.class);
			WampSubscriptionSubscribedEvent wampEvent2 = (WampSubscriptionSubscribedEvent) event;

			assertThat(wampEvent2.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent2.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent2.getSubscriptionDetail().getCreatedTimeMillis())
					.isGreaterThan(0);
			assertThat(wampEvent2.getSubscriptionDetail().getMatchPolicy())
					.isEqualTo(MatchPolicy.EXACT);
			assertThat(wampEvent2.getSubscriptionDetail().getTopic()).isEqualTo("topic");

			this.eventsBean.resetCounter();
			result.reset();
			subscribeMessage = new SubscribeMessage(1, "topic");
			sendMessage(DataFormat.JSON, wsSession, subscribeMessage);
			subscribedMessage = (SubscribedMessage) result.getWampMessage();
			result.waitAFewSeconds();
			assertThat(this.eventsBean.getMethodCounter()).containsOnlyKeys("subscribed");
			events = this.eventsBean.getMethodCounter().get("subscribed");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampSubscriptionSubscribedEvent.class);
			WampSubscriptionSubscribedEvent wampEvent3 = (WampSubscriptionSubscribedEvent) event;

			assertThat(wampEvent3.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent3.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent3.getSubscriptionDetail().getCreatedTimeMillis())
					.isGreaterThan(0);
			assertThat(wampEvent3.getSubscriptionDetail().getMatchPolicy())
					.isEqualTo(MatchPolicy.EXACT);
			assertThat(wampEvent3.getSubscriptionDetail().getTopic()).isEqualTo("topic");

			this.eventsBean.resetCounter();
			result.reset();
			UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(11,
					subscribedMessage.getSubscriptionId());
			sendMessage(DataFormat.JSON, wsSession, unsubscribeMessage);
			result.getWampMessage();
			result.waitAFewSeconds();
			assertThat(this.eventsBean.getMethodCounter())
					.containsOnlyKeys("unsubscribed", "subscriptionDeleted");
			events = this.eventsBean.getMethodCounter().get("unsubscribed");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampSubscriptionUnsubscribedEvent.class);
			WampSubscriptionUnsubscribedEvent wampEvent5 = (WampSubscriptionUnsubscribedEvent) event;
			assertThat(wampEvent5.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent5.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent5.getSubscriptionDetail().getCreatedTimeMillis())
					.isGreaterThan(0);
			assertThat(wampEvent5.getSubscriptionDetail().getMatchPolicy())
					.isEqualTo(MatchPolicy.EXACT);
			assertThat(wampEvent5.getSubscriptionDetail().getTopic()).isEqualTo("topic");

			events = this.eventsBean.getMethodCounter().get("subscriptionDeleted");
			assertThat(events).hasSize(1);
			event = events.get(0);
			assertThat(event).isInstanceOf(WampSubscriptionDeletedEvent.class);
			WampSubscriptionDeletedEvent wampEvent6 = (WampSubscriptionDeletedEvent) event;
			assertThat(wampEvent6.getWampSessionId())
					.isEqualTo(welcomeMessage.getSessionId());
			assertThat(wampEvent6.getWebSocketSessionId()).isNotNull();
			assertThat(wampEvent6.getSubscriptionDetail().getCreatedTimeMillis())
					.isGreaterThan(0);
			assertThat(wampEvent6.getSubscriptionDetail().getMatchPolicy())
					.isEqualTo(MatchPolicy.EXACT);
			assertThat(wampEvent6.getSubscriptionDetail().getTopic()).isEqualTo("topic");
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {
		@Bean
		public EventsBean callParameterTestService() {
			return new EventsBean();
		}

	}

}
