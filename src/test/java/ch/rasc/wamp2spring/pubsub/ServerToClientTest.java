/**
 * Copyright 2017-2017 Ralph Schaer <ralphschaer@gmail.com>
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.assertj.core.data.MapEntry;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.config.EnableWamp;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.rpc.TestDto;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ServerToClientTest.Config.class)
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

	@Configuration
	@EnableAutoConfiguration
	@EnableWamp
	static class Config {
		@Bean
		public ServerToClientService serverToClientService(WampPublisher wampPublisher) {
			return new ServerToClientService(wampPublisher);
		}
	}
}
