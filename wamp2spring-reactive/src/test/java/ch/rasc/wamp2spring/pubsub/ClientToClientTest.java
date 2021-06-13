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

import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ClientToClientTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
public class ClientToClientTest extends BaseWampTest {

	@Test
	public void testNoArgs() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.CBOR)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isNull();
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testOneArgs() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("the argument").build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsExactly("the argument");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testMultipleArgs() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one").addArgument("two").addArgument("three").build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsExactly("one", "two",
					"three");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testArgsKw() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one", 1).addArgument("two", 2).addArgument("three", 3)
					.build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw()).containsOnly(
					MapEntry.entry("one", 1), MapEntry.entry("two", 2),
					MapEntry.entry("three", 3));

			wc2.waitForNothing();
		}
	}

	@Test
	public void testNotExcludeMe() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one", 1).notExcludeMe().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsOnly(MapEntry.entry("one", 1));

			eventMessage = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsOnly(MapEntry.entry("one", 1));
		}
	}

	@Test
	public void testDiscloseMe() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("here").discloseMe().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isEqualTo(wc2.getWampSessionId());
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsOnly("here");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testAcknowledge() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("here").acknowledge().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.isRetained()).isFalse();
			assertThat(eventMessage.getArguments()).containsOnly("here");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			PublishedMessage publishedMessage = wc2.getWampMessage();
			assertThat(publishedMessage.getPublicationId())
					.isEqualTo(eventMessage.getPublicationId());
			assertThat(publishedMessage.getRequestId()).isEqualTo(1L);
		}
	}

	@Test
	public void testExclude() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK);
				WampClient wc3 = new WampClient(DataFormat.CBOR);
				WampClient wc4 = new WampClient(DataFormat.SMILE)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());
			wc3.connect(wampEndpointUrl());
			wc4.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			SubscribedMessage subscribedMessage2 = wc2
					.sendMessageWithResult(subscribeMessage);
			SubscribedMessage subscribedMessage3 = wc3
					.sendMessageWithResult(subscribeMessage);
			wc4.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one").addArgument("two").build();
			wc4.sendMessage(publishMessage);

			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArguments()).containsOnly("one", "two");
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			EventMessage eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.isRetained()).isFalse();
			assertThat(eventMessage2.getArguments()).containsOnly("one", "two");
			assertThat(eventMessage2.getArgumentsKw()).isNull();

			EventMessage eventMessage3 = (EventMessage) wc3.getWampMessage();
			assertThat(eventMessage3.getSubscriptionId())
					.isEqualTo(subscribedMessage3.getSubscriptionId());
			assertThat(eventMessage3.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage3.getTopic()).isNull();
			assertThat(eventMessage3.getPublisher()).isNull();
			assertThat(eventMessage3.isRetained()).isFalse();
			assertThat(eventMessage3.getArguments()).containsOnly("one", "two");
			assertThat(eventMessage3.getArgumentsKw()).isNull();

			wc4.waitForNothing();

			publishMessage = new PublishMessage.Builder(1L, "topic").addArgument("three")
					.addExclude(wc1.getWampSessionId()).addExclude(wc3.getWampSessionId())
					.build();
			wc4.sendMessage(publishMessage);

			eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isNull();
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.isRetained()).isFalse();
			assertThat(eventMessage2.getArguments()).containsOnly("three");
			assertThat(eventMessage2.getArgumentsKw()).isNull();

			wc1.waitForNothing();
			wc3.waitForNothing();
			wc4.waitForNothing();
		}
	}

	@Test
	public void testEligible() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK);
				WampClient wc3 = new WampClient(DataFormat.CBOR);
				WampClient wc4 = new WampClient(DataFormat.SMILE)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());
			wc3.connect(wampEndpointUrl());
			wc4.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);
			wc3.sendMessageWithResult(subscribeMessage);
			wc4.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("four").addEligible(wc1.getWampSessionId())
					.addEligible(wc3.getWampSessionId()).build();
			wc4.sendMessage(publishMessage);

			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isNull();
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArguments()).containsOnly("four");
			assertThat(eventMessage1.getArgumentsKw()).isNull();

			EventMessage eventMessage3 = (EventMessage) wc3.getWampMessage();
			assertThat(eventMessage3.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage3.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage3.getTopic()).isNull();
			assertThat(eventMessage3.getPublisher()).isNull();
			assertThat(eventMessage3.isRetained()).isFalse();
			assertThat(eventMessage3.getArguments()).containsOnly("four");
			assertThat(eventMessage3.getArgumentsKw()).isNull();

			wc2.waitForNothing();
			wc4.waitForNothing();
		}
	}

	@Test
	public void testPrefix() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.JSON);
				WampClient wc2 = new WampClient(DataFormat.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "news",
					MatchPolicy.PREFIX);
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			wc2.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "news.world")
					.addArgument("test").addArgument("test", 1012).build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("news.world");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArguments()).containsExactly("test");
			assertThat(eventMessage1.getArgumentsKw())
					.containsOnly(MapEntry.entry("test", 1012));

			wc2.waitForNothing();

			publishMessage = new PublishMessage.Builder(1L, "news.business")
					.addArgument("company").addArgument("test", 1013).build();
			wc2.sendMessage(publishMessage);

			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("news.business");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArguments()).containsExactly("company");
			assertThat(eventMessage1.getArgumentsKw())
					.containsOnly(MapEntry.entry("test", 1013));

			wc2.waitForNothing();

			publishMessage = new PublishMessage.Builder(1L, "newArticle")
					.addArgument("test", 1014).build();
			wc2.sendMessage(publishMessage);

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

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "crud..create",
					MatchPolicy.WILDCARD);
			SubscribedMessage subscribedMessage1 = wc1
					.sendMessageWithResult(subscribeMessage);
			SubscribedMessage subscribedMessage2 = wc2
					.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L,
					"crud.user.create").addArgument("id", 11)
							.addArgument("username", "joe").build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("crud.user.create");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArguments()).isEmpty();
			assertThat(eventMessage1.getArgumentsKw()).containsOnly(
					MapEntry.entry("id", 11), MapEntry.entry("username", "joe"));

			wc2.waitForNothing();

			publishMessage = new PublishMessage.Builder(1L, "crud.company.create")
					.addArgument("id", 12).addArgument("name", "solar inc.")
					.notExcludeMe().build();
			wc2.sendMessage(publishMessage);

			eventMessage1 = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage1.getSubscriptionId())
					.isEqualTo(subscribedMessage1.getSubscriptionId());
			assertThat(eventMessage1.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage1.getTopic()).isEqualTo("crud.company.create");
			assertThat(eventMessage1.getPublisher()).isNull();
			assertThat(eventMessage1.getArguments()).isEmpty();
			assertThat(eventMessage1.isRetained()).isFalse();
			assertThat(eventMessage1.getArgumentsKw()).containsOnly(
					MapEntry.entry("id", 12), MapEntry.entry("name", "solar inc."));

			EventMessage eventMessage2 = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage2.getSubscriptionId())
					.isEqualTo(subscribedMessage2.getSubscriptionId());
			assertThat(eventMessage2.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage2.getTopic()).isEqualTo("crud.company.create");
			assertThat(eventMessage2.getPublisher()).isNull();
			assertThat(eventMessage2.getArguments()).isEmpty();
			assertThat(eventMessage2.isRetained()).isFalse();
			assertThat(eventMessage2.getArgumentsKw()).containsOnly(
					MapEntry.entry("id", 12), MapEntry.entry("name", "solar inc."));

			publishMessage = new PublishMessage.Builder(1L, "crud.create")
					.addArgument("id", 13).build();
			wc2.sendMessage(publishMessage);

			wc1.waitForNothing();
			wc2.waitForNothing();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {
		// nothing here
	}

}
