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
package ch.rasc.wamp2spring.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.MapEntry;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import ch.rasc.wamp2spring.config.EnableWamp;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ClientToClient.Config.class)
public class ClientToClient extends BaseWampTest {

	@Test
	public void testOnePubSubExactNoArgs() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).isNull();
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testOnePubSubExactOneArgs() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("the argument").build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsExactly("the argument");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testOnePubSubExactMultipleArgs() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one").addArgument("two").addArgument("three").build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsExactly("one", "two",
					"three");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testOnePubSubExactArgsKw() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
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
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw()).containsOnly(
					MapEntry.entry("one", 1), MapEntry.entry("two", 2),
					MapEntry.entry("three", 3));

			wc2.waitForNothing();
		}
	}

	public void testOnePubSubExactNotExcludeMe() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);
			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("one", 1).notExcludeMe().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsOnly(MapEntry.entry("one", 1));

			eventMessage = (EventMessage) wc2.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).isEmpty();
			assertThat(eventMessage.getArgumentsKw())
					.containsOnly(MapEntry.entry("one", 1));
		}
	}

	@Test
	public void testOnePubSubExactDiscloseMe() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("here").discloseMe().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isEqualTo(wc2.getWampSessionId());
			assertThat(eventMessage.getArguments()).containsOnly("here");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			wc2.waitForNothing();
		}
	}

	@Test
	public void testOnePubSubExactAcknowledge() throws Exception {
		try (WampClient wc1 = new WampClient(Protocol.JSON);
				WampClient wc2 = new WampClient(Protocol.MSGPACK)) {

			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
			SubscribedMessage subscribedMessage = wc1
					.sendMessageWithResult(subscribeMessage);

			PublishMessage publishMessage = new PublishMessage.Builder(1L, "topic")
					.addArgument("here").acknowledge().build();
			wc2.sendMessage(publishMessage);

			EventMessage eventMessage = (EventMessage) wc1.getWampMessage();
			assertThat(eventMessage.getSubscriptionId())
					.isEqualTo(subscribedMessage.getSubscriptionId());
			assertThat(eventMessage.getPublicationId()).isGreaterThan(0L);
			assertThat(eventMessage.getTopic()).isNull();
			assertThat(eventMessage.getPublisher()).isNull();
			assertThat(eventMessage.getArguments()).containsOnly("here");
			assertThat(eventMessage.getArgumentsKw()).isNull();

			PublishedMessage publishedMessage = wc2.getWampMessage();
			assertThat(publishedMessage.getPublicationId())
					.isEqualTo(eventMessage.getPublicationId());
			assertThat(publishedMessage.getRequestId()).isEqualTo(1L);
		}
	}

	@SpringBootApplication
	@EnableWamp
	static class Config {
		// nothing here
	}

}
