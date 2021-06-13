/**
 * Copyright 2017-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = RetentionTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
public class RetentionTest extends BaseWampTest {

	@Autowired
	private RetentionService retentionService;

	@Test
	@DisabledIfSystemProperty(named = "CI", matches = "true")
	public void testExact() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK);
				WampClient wc3 = new WampClient(DataFormat.CBOR);
				WampClient wc4 = new WampClient(DataFormat.SMILE)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "humidity");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long subscriptionId = subscribedMessage.getSubscriptionId();

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "humidity")
					.acknowledge().retain().addArgument(45).build();
			PublishedMessage publishedMessage = wc2.sendMessageWithResult(publishMessage);
			assertThat(publishedMessage.getRequestId())
					.isEqualTo(publishMessage.getRequestId());
			long publicationId = publishedMessage.getPublicationId();

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscriptionId);
			assertThat(eventMessage.getPublicationId()).isEqualTo(publicationId);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsExactly(45);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc3.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(3, "humidity");
			subscribedMessage = wc3.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w3SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3SubscriptionId).isEqualTo(subscriptionId);
			wc3.waitForNothing();

			wc4.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(4, "humidity", true);
			wc4.getResult().reset(2);
			wc4.sendMessage(subscribeMessage);
			List<WampMessage> result = wc4.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w4SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w4SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w4SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).containsExactly(45);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc4.close();
			publishMessage = new PublishMessage.Builder(1L, "humidity").acknowledge()
					.retain().addArgument(48).build();
			publishedMessage = wc2.sendMessageWithResult(publishMessage);
			assertThat(publishedMessage.getRequestId())
					.isEqualTo(publishMessage.getRequestId());
			publicationId = publishedMessage.getPublicationId();

			TimeUnit.SECONDS.sleep(2);

			wc4.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(4, "humidity", true);
			wc4.getResult().reset(2);
			wc4.sendMessage(subscribeMessage);
			TimeUnit.SECONDS.sleep(2);
			result = wc4.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			w4SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w4SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w4SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).containsExactly(48);
			assertThat(eventMessage.getArgumentsKw()).isNull();
		}
	}

	@Test
	@DisabledIfSystemProperty(named = "CI", matches = "true")
	public void testPrefix() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK);
				WampClient wc3 = new WampClient(DataFormat.CBOR)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());
			wc3.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "temperature",
					MatchPolicy.PREFIX);
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long subscriptionId = subscribedMessage.getSubscriptionId();

			PublishMessage publishMessage = this.retentionService
					.publishMessageBuilder("temperature.london").addArgument(27.12)
					.retain().build();
			this.retentionService.getWampPublisher().publish(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("temperature.london");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsExactly(27.12);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			subscribeMessage = new SubscribeMessage(2, "temperature", MatchPolicy.PREFIX);
			subscribedMessage = wc2.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w2SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w2SubscriptionId).isEqualTo(subscriptionId);
			wc2.waitForNothing();

			subscribeMessage = new SubscribeMessage(3, "temperature", MatchPolicy.PREFIX,
					true);
			wc3.getResult().reset(2);
			wc3.sendMessage(subscribeMessage);
			List<WampMessage> result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w3SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("temperature.london");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).containsExactly(27.12);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			this.retentionService.getWampPublisher()
					.publish(this.retentionService
							.publishMessageBuilder("temperature.london").addArgument(28.5)
							.retain().build());
			this.retentionService.getWampPublisher()
					.publish(this.retentionService
							.publishMessageBuilder("temperature.paris").addArgument(15.4)
							.retain().build());

			TimeUnit.SECONDS.sleep(2);

			wc3.close();
			wc3.connect(wampEndpointUrl());

			subscribeMessage = new SubscribeMessage(4, "temperature", MatchPolicy.PREFIX,
					true);
			wc3.getResult().reset(3);
			wc3.sendMessage(subscribeMessage);
			result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(3);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w3bSubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3bSubscriptionId).isEqualTo(subscriptionId);

			boolean londonCheck = false;
			boolean parisCheck = false;
			eventMessage = (EventMessage) result.get(1);

			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isIn("temperature.london",
					"temperature.paris");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			if ("temperature.london".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(28.5);
				londonCheck = true;
			}
			else {
				assertThat(eventMessage.getArguments()).containsExactly(15.4);
				parisCheck = true;
			}
			assertThat(eventMessage.getArgumentsKw()).isNull();

			eventMessage = (EventMessage) result.get(2);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isIn("temperature.london",
					"temperature.paris");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			if ("temperature.london".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(28.5);
				if (londonCheck) {
					Assertions.fail("Wrong message");
				}
			}
			else {
				assertThat(eventMessage.getArguments()).containsExactly(15.4);
				if (parisCheck) {
					Assertions.fail("Wrong message");
				}
			}
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc3.getResult().reset();
			this.retentionService.getWampPublisher()
					.publish(this.retentionService
							.publishMessageBuilder("temperature.oslo").addArgument(3.6)
							.retain().build());
			eventMessage = wc3.getWampMessage();
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("temperature.oslo");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsExactly(3.6);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc3.close();
			wc3.connect(wampEndpointUrl());

			this.retentionService.getWampPublisher()
					.publish(this.retentionService
							.publishMessageBuilder("temperature.london").addArgument(23.6)
							.retain().build());
			this.retentionService.getWampPublisher()
					.publish(this.retentionService
							.publishMessageBuilder("temperature.paris").addArgument(11.3)
							.build());
			this.retentionService.getWampPublisher().publish(this.retentionService
					.publishMessageBuilder("temperature.oslo").addArgument(2.9).build());

			TimeUnit.SECONDS.sleep(2);

			subscribeMessage = new SubscribeMessage(4, "temperature", MatchPolicy.PREFIX,
					true);
			wc3.getResult().reset(4);
			wc3.sendMessage(subscribeMessage);
			result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(4);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			w3bSubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3bSubscriptionId).isEqualTo(subscriptionId);

			londonCheck = false;
			parisCheck = false;
			boolean osloCheck = false;
			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isIn("temperature.london",
					"temperature.paris", "temperature.oslo");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			if ("temperature.london".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(23.6);
				londonCheck = true;
			}
			else if ("temperature.paris".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(15.4);
				parisCheck = true;
			}
			else {
				assertThat(eventMessage.getArguments()).containsExactly(3.6);
				osloCheck = true;
			}
			assertThat(eventMessage.getArgumentsKw()).isNull();

			eventMessage = (EventMessage) result.get(2);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isIn("temperature.london",
					"temperature.paris", "temperature.oslo");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			if ("temperature.london".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(23.6);
				if (londonCheck) {
					Assertions.fail("wrong message");
				}
				londonCheck = true;
			}
			else if ("temperature.paris".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(15.4);
				if (parisCheck) {
					Assertions.fail("wrong message");
				}
				parisCheck = true;
			}
			else {
				assertThat(eventMessage.getArguments()).containsExactly(3.6);
				if (osloCheck) {
					Assertions.fail("wrong message");
				}
				osloCheck = true;
			}
			assertThat(eventMessage.getArgumentsKw()).isNull();

			eventMessage = (EventMessage) result.get(3);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isIn("temperature.london",
					"temperature.paris", "temperature.oslo");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			if ("temperature.london".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(23.6);
				if (londonCheck) {
					Assertions.fail("wrong message");
				}
			}
			else if ("temperature.paris".equals(eventMessage.getTopic())) {
				assertThat(eventMessage.getArguments()).containsExactly(15.4);
				if (parisCheck) {
					Assertions.fail("wrong message");
				}
			}
			else {
				assertThat(eventMessage.getArguments()).containsExactly(3.6);
				if (osloCheck) {
					Assertions.fail("wrong message");
				}
			}
			assertThat(eventMessage.getArgumentsKw()).isNull();
		}
	}

	@Test
	@DisabledIfSystemProperty(named = "CI", matches = "true")
	public void testWildcard() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK);
				WampClient wc3 = new WampClient(DataFormat.CBOR)) {

			wc1.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "crud..create",
					MatchPolicy.WILDCARD);
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long subscriptionId = subscribedMessage.getSubscriptionId();

			PublishMessage publishMessage = this.retentionService
					.publishMessageBuilder("crud.user.create").addArgument("id", 1)
					.retain().build();
			this.retentionService.getWampPublisher().publish(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsExactly(MapEntry.entry("id", 1));

			wc2.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(2, "crud..create",
					MatchPolicy.WILDCARD);
			subscribedMessage = wc2.sendMessageWithResult(subscribeMessage);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w2SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w2SubscriptionId).isEqualTo(subscriptionId);
			wc2.waitForNothing();

			wc3.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(3, "crud..create",
					MatchPolicy.WILDCARD, true);
			wc3.getResult().reset(2);
			wc3.sendMessage(subscribeMessage);
			List<WampMessage> result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			long w3SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsExactly(MapEntry.entry("id", 1));

			publishMessage = this.retentionService
					.publishMessageBuilder("crud.user.update").addArgument("id", 2)
					.retain().build();
			this.retentionService.getWampPublisher().publish(publishMessage);
			wc3.getResult().reset();
			wc3.waitForNothing();

			publishMessage = this.retentionService
					.publishMessageBuilder("crud.user.create").addArgument("id", 3)
					.retain().build();
			this.retentionService.getWampPublisher().publish(publishMessage);
			TimeUnit.SECONDS.sleep(2);

			eventMessage = (EventMessage) wc3.getWampMessage();
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsExactly(MapEntry.entry("id", 3));

			wc3.close();
			wc3.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(4, "crud..create",
					MatchPolicy.WILDCARD, true);
			wc3.getResult().reset(2);
			wc3.sendMessage(subscribeMessage);
			TimeUnit.SECONDS.sleep(2);
			result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			w3SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsExactly(MapEntry.entry("id", 3));

			wc3.close();
			publishMessage = this.retentionService
					.publishMessageBuilder("crud.user.create").addArgument("id", 11)
					.build();
			this.retentionService.getWampPublisher().publish(publishMessage);
			TimeUnit.SECONDS.sleep(2);

			wc3.connect(wampEndpointUrl());
			subscribeMessage = new SubscribeMessage(5, "crud..create",
					MatchPolicy.WILDCARD, true);
			wc3.getResult().reset(2);
			wc3.sendMessage(subscribeMessage);
			result = wc3.getResult().getWampMessages();
			assertThat(result).hasSize(2);

			subscribedMessage = (SubscribedMessage) result.get(0);
			assertThat(subscribedMessage.getRequestId())
					.isEqualTo(subscribeMessage.getRequestId());
			w3SubscriptionId = subscribedMessage.getSubscriptionId();
			assertThat(w3SubscriptionId).isEqualTo(subscriptionId);

			eventMessage = (EventMessage) result.get(1);
			assertThat(eventMessage.getSubscriptionId()).isEqualTo(w3SubscriptionId);
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0);
			assertThat(eventMessage.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isTrue();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsExactly(MapEntry.entry("id", 3));
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {
		@Bean
		public RetentionService retentionTest(WampPublisher wampPublisher) {
			return new RetentionService(wampPublisher);
		}

		@Bean
		public EventStore eventStore() {
			return new MemoryEventStore() {

				@Override
				public void retain(PublishMessage publishMessage) {
					this.eventRetention.put(publishMessage.getTopic(), publishMessage);
				}

			};
		}
	}

}
