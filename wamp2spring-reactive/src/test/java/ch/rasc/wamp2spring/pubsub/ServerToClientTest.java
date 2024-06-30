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
package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
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
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.rpc.TestDto;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ServerToClientTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
public class ServerToClientTest extends BaseWampTest {

	@Autowired
	private ServerToClientService serverToClientService;

	@Test
	public void testNoArguments() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {
			wc.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc
					.sendMessageWithResult(subscribeMessage);

			this.serverToClientService.getWampPublisher().publishToAll("topic");
			EventMessage eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).isNull();
			assertThat(eventMessage.getArgumentsKw()).isNull();

			this.serverToClientService.getWampPublisher().publishToAll("topic2");
			wc.waitForNothing();
		}
	}

	@Test
	public void testArguments() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {
			wc.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc
					.sendMessageWithResult(subscribeMessage);

			WampPublisher wampPublisher = this.serverToClientService.getWampPublisher();

			wampPublisher.publishToAll("topic", 1);
			EventMessage eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsExactly(1);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wampPublisher.publishToAll("topic", "one", "two");
			eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsExactly("one", "two");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wampPublisher.publishToAll("topic", Arrays.asList(1.1, 2.2, 3.3));
			eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsExactly(1.1, 2.2, 3.3);
			assertThat(eventMessage.getArgumentsKw()).isNull();

			Map<String, Object> map = new HashMap<>();
			map.put("id", 1);
			map.put("name", "john");
			map.put("age", 27);
			wampPublisher.publishToAll("topic", map);
			eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw()).containsOnly(
					MapEntry.entry("id", 1), MapEntry.entry("name", "john"),
					MapEntry.entry("age", 27));

			map = new HashMap<>();
			map.put("id", 2);
			Set<Integer> set = new HashSet<>();
			set.add(17);
			set.add(19);
			PublishMessage publishMessage = wampPublisher.publishMessageBuilder("topic")
					.arguments(set).arguments(map).build();
			wampPublisher.publish(publishMessage);
			eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsOnly(17, 19);
			assertThat(eventMessage.getArgumentsKw())
					.containsOnly(MapEntry.entry("id", 2));

			TestDto testDto = new TestDto(23, "name");
			wampPublisher.publishToAll("topic", testDto);
			eventMessage = (EventMessage) wc.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			map = new HashMap<>();
			map.put("id", 23);
			map.put("name", "name");
			assertThat(eventMessage.getArguments()).containsExactly(map);
			assertThat(eventMessage.getArgumentsKw()).isNull();
		}
	}

	@Test
	public void testPrefix() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribedMessage subscribedMessage1 = wc1.sendMessageWithResult(
					new SubscribeMessage(1, "news", MatchPolicy.PREFIX));
			wc2.sendMessageWithResult(new SubscribeMessage(1, "news", MatchPolicy.EXACT));

			this.serverToClientService.getWampPublisher().publishToAll("news.business",
					"argument");
			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("news.business");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly("argument");
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher().publishToAll("news.sport",
					"bike");
			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("news.sport");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly("bike");
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher().publishToAll("new.world",
					"eclipse");
			wc1.waitForNothing();
			wc2.waitForNothing();
		}
	}

	@Test
	public void testWildcard() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribedMessage subscribedMessage1 = wc1.sendMessageWithResult(
					new SubscribeMessage(1, "crud..create", MatchPolicy.WILDCARD));
			wc2.sendMessageWithResult(
					new SubscribeMessage(1, "crud..create", MatchPolicy.EXACT));

			this.serverToClientService.getWampPublisher().publishToAll("crud.user.create",
					1);
			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly(1);
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher()
					.publishToAll("crud.company.create", "tower");
			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("crud.company.create");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly("tower");
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher().publishToAll("crud.user.update",
					"2");
			wc1.waitForNothing();
			wc2.waitForNothing();
		}
	}

	@Test
	public void testEligible() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			SubscribedMessage subscribedMessage2 = wc2
					.sendMessageWithResult(subscribeMessage);

			this.serverToClientService.getWampPublisher()
					.publishTo(wc1.getWampSessionId(), "topic", 1, 2, 3);
			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly(1, 2, 3);
			assertThat(eventMessage1.getArgumentsKw()).isNull();
			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher()
					.publishTo(wc2.getWampSessionId(), "topic", 5);
			EventMessage eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.getArguments()).containsExactly(5);
			assertThat(eventMessage2.getArgumentsKw()).isNull();
			wc1.waitForNothing();

			this.serverToClientService.getWampPublisher().publishTo(
					Arrays.asList(wc1.getWampSessionId(), wc2.getWampSessionId()),
					"topic", 6);
			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).containsExactly(6);
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.getArguments()).containsExactly(6);
			assertThat(eventMessage2.getArgumentsKw()).isNull();

			this.serverToClientService.getWampPublisher().publishTo(Arrays.asList(-1L),
					"topic", 7);
			wc1.waitForNothing();
			wc2.waitForNothing();
		}
	}

	@Test
	public void testExclude() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			SubscribedMessage subscribedMessage2 = wc2
					.sendMessageWithResult(subscribeMessage);

			this.serverToClientService.getWampPublisher().publishToAllExcept(
					wc2.getWampSessionId(), "topic", Collections.singletonMap("id", 111));
			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).isEmpty();
			assertThat(eventMessage1.getArgumentsKw())
					.containsOnly(MapEntry.entry("id", 111));
			wc2.waitForNothing();

			this.serverToClientService.getWampPublisher().publishToAllExcept(
					wc1.getWampSessionId(), "topic", Collections.singletonMap("id", 112));
			EventMessage eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.getArguments()).isEmpty();
			assertThat(eventMessage2.getArgumentsKw())
					.containsOnly(MapEntry.entry("id", 112));
			wc1.waitForNothing();

			this.serverToClientService.getWampPublisher().publishToAllExcept(
					Arrays.asList(-1L), "topic", Collections.singletonMap("id", 113));
			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).isEmpty();
			assertThat(eventMessage1.getArgumentsKw())
					.containsOnly(MapEntry.entry("id", 113));

			eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.getArguments()).isEmpty();
			assertThat(eventMessage2.getArgumentsKw())
					.containsOnly(MapEntry.entry("id", 113));

			this.serverToClientService.getWampPublisher().publishToAllExcept(
					Arrays.asList(wc1.getWampSessionId(), wc2.getWampSessionId()),
					"topic", Collections.singletonMap("id", 114));
			wc1.waitForNothing();
			wc2.waitForNothing();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {
		@Bean
		public ServerToClientService serverToClientService(WampPublisher wampPublisher) {
			return new ServerToClientService(wampPublisher);
		}
	}
}
