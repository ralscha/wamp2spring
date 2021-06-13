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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.util.StopWatch;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

public class SubscriptionRegistryTest {

	private SubscriptionRegistry subscriptionRegistry;

	@BeforeEach
	public void setup() {
		this.subscriptionRegistry = new SubscriptionRegistry();
	}

	@Test
	public void testEmptyRegistry() {
		EnumMap<MatchPolicy, List<Long>> m = this.subscriptionRegistry
				.listSubscriptions();
		assertThat(m.get(MatchPolicy.EXACT)).isEmpty();
		assertThat(m.get(MatchPolicy.PREFIX)).isEmpty();
		assertThat(m.get(MatchPolicy.WILDCARD)).isEmpty();

		assertThat(this.subscriptionRegistry.lookupSubscription("aTopic", null)).isNull();
		assertThat(
				this.subscriptionRegistry.lookupSubscription("aTopic", MatchPolicy.EXACT))
						.isNull();
		assertThat(this.subscriptionRegistry.lookupSubscription("aTopic",
				MatchPolicy.PREFIX)).isNull();
		assertThat(this.subscriptionRegistry.lookupSubscription("aTopic",
				MatchPolicy.WILDCARD)).isNull();

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("aTopic")).isEmpty();
		assertThat(this.subscriptionRegistry.getSubscription(1L)).isNull();
		assertThat(this.subscriptionRegistry.listSubscribers(1L)).isEmpty();
		assertThat(this.subscriptionRegistry.countSubscribers(1L)).isNull();
		assertThat(this.subscriptionRegistry.hasSubscribers("aTopic")).isFalse();
	}

	@Test
	public void testSubscribeOneSubscriber() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result.getWampSessionId()).isEqualTo(123L);
		assertThat(result.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.EXACT);
		assertThat(result.getSubscription().getCreatedTimeMillis()).isGreaterThan(0);
		assertThat(result.getSubscription().getEventListenerHandlerMethods()).isNull();
		assertThat(result.getSubscription().getSubscribers()).hasSize(1);
		assertThat(result.getSubscription().getTopic()).isEqualTo("topic");
		assertThat(result.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result.getSubscription().getSubscriptionId(), 123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribeUnsubscribe() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result.getSubscription().getSubscriptionId(), 123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topica")).isEmpty();

		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(3,
				result.getSubscription().getSubscriptionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		UnsubscribeResult uresult = this.subscriptionRegistry
				.unsubscribe(unsubscribeMessage);
		assertThat(uresult.getError()).isNull();
		assertThat(uresult.isDeleted()).isTrue();
		assertThat(uresult.getWampSessionId()).isEqualTo(123L);
		assertThat(uresult.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.EXACT);
		assertThat(uresult.getSubscription().getSubscriptionId())
				.isEqualTo(result.getSubscription().getSubscriptionId());
		assertThat(uresult.getSubscription().getTopic()).isEqualTo("topic");
		assertThat(uresult.getSubscription().getSubscribers()).isEmpty();

		ra = new RegistryAssert();
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic")).isEmpty();
	}

	@Test
	public void testSubscribeTwoUnsubscribeOne() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");
		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);

		subscribeMessage = new SubscribeMessage(2, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 321L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "2");
		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result1.getSubscription().getSubscriptionId(), 123L);
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result2.getSubscription().getSubscriptionId(), 321L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result2.getSubscription().getSubscriptionId());

		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(3,
				result1.getSubscription().getSubscriptionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");

		UnsubscribeResult uresult = this.subscriptionRegistry
				.unsubscribe(unsubscribeMessage);
		assertThat(uresult.getError()).isNull();
		assertThat(uresult.isDeleted()).isFalse();
		assertThat(uresult.getWampSessionId()).isEqualTo(123L);
		assertThat(uresult.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.EXACT);
		assertThat(uresult.getSubscription().getSubscriptionId())
				.isEqualTo(result1.getSubscription().getSubscriptionId());
		assertThat(uresult.getSubscription().getTopic()).isEqualTo("topic");
		assertThat(uresult.getSubscription().getSubscribers()).hasSize(1);

		ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result2.getSubscription().getSubscriptionId(), 321L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result2.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribeUnsubscribePrefix() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1,
				"com.myapp.topic.emergency", MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.topic.emergency",
				result.getSubscription().getSubscriptionId(), 123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.11"))
						.containsExactly(result.getSubscription().getSubscriptionId());
		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.topic.emerge"))
						.isEmpty();

		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(3,
				result.getSubscription().getSubscriptionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		UnsubscribeResult uresult = this.subscriptionRegistry
				.unsubscribe(unsubscribeMessage);
		assertThat(uresult.isDeleted()).isTrue();
		assertThat(uresult.getError()).isNull();
		assertThat(uresult.getWampSessionId()).isEqualTo(123L);
		assertThat(uresult.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.PREFIX);
		assertThat(uresult.getSubscription().getSubscriptionId())
				.isEqualTo(result.getSubscription().getSubscriptionId());
		assertThat(uresult.getSubscription().getTopic())
				.isEqualTo("com.myapp.topic.emergency");
		assertThat(uresult.getSubscription().getSubscribers()).isEmpty();

		ra = new RegistryAssert();
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.11")).isEmpty();
		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.topic.emerge"))
						.isEmpty();
	}

	@Test
	public void testSubscribeUnsubscribeWildcard() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1,
				"com.myapp..userevent", MatchPolicy.WILDCARD);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.WILDCARD, "com.myapp..userevent",
				result.getSubscription().getSubscriptionId(), 123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent"))
						.containsExactly(result.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent.bar")).isEmpty();

		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(3,
				result.getSubscription().getSubscriptionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		UnsubscribeResult uresult = this.subscriptionRegistry
				.unsubscribe(unsubscribeMessage);
		assertThat(uresult.isDeleted()).isTrue();
		assertThat(uresult.getError()).isNull();
		assertThat(uresult.getWampSessionId()).isEqualTo(123L);
		assertThat(uresult.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.WILDCARD);
		assertThat(uresult.getSubscription().getSubscriptionId())
				.isEqualTo(result.getSubscription().getSubscriptionId());
		assertThat(uresult.getSubscription().getTopic())
				.isEqualTo("com.myapp..userevent");
		assertThat(uresult.getSubscription().getSubscribers()).isEmpty();

		ra = new RegistryAssert();
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent")).isEmpty();
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent.bar")).isEmpty();
	}

	@Test
	public void testSubscribeSameSubscriberSameTopicTwice() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");
		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);

		subscribeMessage = new SubscribeMessage(2, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");
		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result1.getSubscription().getSubscriptionId(), 123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result2.getSubscription().getSubscriptionId());

		assertThat(result1.getSubscription().getSubscriptionId())
				.isEqualTo(result2.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribeTwoSubscribersSameTopic() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");
		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);

		assertThat(result1.getWampSessionId()).isEqualTo(123L);
		assertThat(result1.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.EXACT);
		assertThat(result1.getSubscription().getCreatedTimeMillis()).isGreaterThan(0);
		assertThat(result1.getSubscription().getEventListenerHandlerMethods()).isNull();
		assertThat(result1.getSubscription().getSubscribers()).hasSize(1);
		assertThat(result1.getSubscription().getTopic()).isEqualTo("topic");
		assertThat(result1.isCreated()).isTrue();

		subscribeMessage = new SubscribeMessage(2, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 321L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "2");
		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);

		assertThat(result2.getWampSessionId()).isEqualTo(321L);
		assertThat(result2.getSubscription().getMatchPolicy())
				.isEqualTo(MatchPolicy.EXACT);
		assertThat(result2.getSubscription().getCreatedTimeMillis()).isGreaterThan(0);
		assertThat(result2.getSubscription().getEventListenerHandlerMethods()).isNull();
		assertThat(result2.getSubscription().getSubscribers()).hasSize(2);
		assertThat(result2.getSubscription().getTopic()).isEqualTo("topic");
		assertThat(result2.isCreated()).isFalse();

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result1.getSubscription().getSubscriptionId(), 123L);
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result2.getSubscription().getSubscriptionId(), 321L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result2.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribeTwoSubscribersDifferentTopic() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "1");
		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result1.isCreated()).isTrue();

		subscribeMessage = new SubscribeMessage(2, "topic.second");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 321L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "2");
		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result2.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.EXACT, "topic",
				result1.getSubscription().getSubscriptionId(), 123L);
		ra.addSubscriber(MatchPolicy.EXACT, "topic.second",
				result2.getSubscription().getSubscriptionId(), 321L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic"))
				.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("topic.second"))
				.containsExactly(result2.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribePrefix() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1,
				"com.myapp.topic.emergency", MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		long subscriptionId = result.getSubscription().getSubscriptionId();
		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.topic.emergency", subscriptionId,
				123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.11"))
						.containsExactly(subscriptionId);
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency-low"))
						.containsExactly(subscriptionId);
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.category.severe"))
						.containsExactly(subscriptionId);
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency"))
						.containsExactly(subscriptionId);

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.topic.emerge"))
						.isEmpty();
	}

	@Test
	public void testSubscribeWildcard() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1,
				"com.myapp..userevent", MatchPolicy.WILDCARD);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		long subscriptionId = result.getSubscription().getSubscriptionId();
		ra.addSubscriber(MatchPolicy.WILDCARD, "com.myapp..userevent", subscriptionId,
				123L);
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent"))
						.containsExactly(subscriptionId);
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.bar.userevent"))
						.containsExactly(subscriptionId);
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.a12.userevent"))
						.containsExactly(subscriptionId);

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent.bar")).isEmpty();
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.foo.user"))
				.isEmpty();
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp2.foo.userevent")).isEmpty();
	}

	@Test
	public void testUnsubscribeNonExistentSubscription() {
		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(33, 1);
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		UnsubscribeResult uresult = this.subscriptionRegistry
				.unsubscribe(unsubscribeMessage);
		assertThat(uresult.getError()).isEqualTo(WampError.NO_SUCH_SUBSCRIPTION);
		assertThat(uresult.getWampSessionId()).isEqualTo(123L);
		assertThat(uresult.getSubscription()).isNull();
	}

	@Test
	public void testAllInOne() {
		Random random = new Random();
		String[] topics = { "help", "com.myapp..userevent", "com.myapp.topic.emergency" };
		MatchPolicy[] matchPolicy = { MatchPolicy.EXACT, MatchPolicy.WILDCARD,
				MatchPolicy.PREFIX };

		List<UnsubscribeMessage> unsubs = new ArrayList<>();

		RegistryAssert ra = new RegistryAssert();
		for (int i = 0; i < 10_000; i++) {
			int ix = random.nextInt(3);
			SubscribeMessage subscribeMessage = new SubscribeMessage(i + 1, topics[ix],
					matchPolicy[ix]);
			subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					Long.valueOf(500_000 + i));
			subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws" + i);

			SubscribeResult result = this.subscriptionRegistry
					.subscribe(subscribeMessage);

			ra.addSubscriber(matchPolicy[ix], topics[ix],
					result.getSubscription().getSubscriptionId(),
					Long.valueOf(500_000 + i));

			UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(700_000 + i,
					result.getSubscription().getSubscriptionId());
			unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					Long.valueOf(500_000 + i));
			unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
					"ws" + i);
			unsubs.add(unsubscribeMessage);
		}
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("help"))
				.containsExactly(ra.subscriptionId(topics[0], matchPolicy[0]));
		assertThat(this.subscriptionRegistry.hasSubscribers("help")).isTrue();
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("helpa")).isEmpty();
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent"))
						.containsExactly(ra.subscriptionId(topics[1], matchPolicy[1]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.bar.userevent"))
						.containsExactly(ra.subscriptionId(topics[1], matchPolicy[1]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.a12.userevent"))
						.containsExactly(ra.subscriptionId(topics[1], matchPolicy[1]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.foo.userevent.bar")).isEmpty();
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.foo.user"))
				.isEmpty();
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp2.foo.userevent")).isEmpty();

		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.11"))
						.containsExactly(ra.subscriptionId(topics[2], matchPolicy[2]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency-low"))
						.containsExactly(ra.subscriptionId(topics[2], matchPolicy[2]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency.category.severe"))
						.containsExactly(ra.subscriptionId(topics[2], matchPolicy[2]));
		assertThat(this.subscriptionRegistry
				.getMatchSubscriptions("com.myapp.topic.emergency"))
						.containsExactly(ra.subscriptionId(topics[2], matchPolicy[2]));

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.topic.emerge"))
						.isEmpty();

		for (UnsubscribeMessage unsub : unsubs) {
			UnsubscribeResult uresult = this.subscriptionRegistry.unsubscribe(unsub);
			assertThat(uresult.getError()).isNull();
		}

		ra = new RegistryAssert();
		assertRegistry(ra);

		assertThat(this.subscriptionRegistry.getMatchSubscriptions("help")).isEmpty();
		assertThat(this.subscriptionRegistry.hasSubscribers("help")).isFalse();
	}

	@Test
	public void manyRegistryQueries() {
		Random random = new Random();

		String[] topics = { "help", "com.myapp..userevent", "com.myapp.topic.emergency" };
		MatchPolicy[] matchPolicy = { MatchPolicy.EXACT, MatchPolicy.WILDCARD,
				MatchPolicy.PREFIX };

		List<UnsubscribeMessage> unsubs = new ArrayList<>();
		List<Integer> ixs = new ArrayList<>();

		RegistryAssert ra = new RegistryAssert();
		for (int i = 0; i < 2_000; i++) {
			int ix = random.nextInt(3);
			SubscribeMessage subscribeMessage = new SubscribeMessage(i + 1, topics[ix],
					matchPolicy[ix]);
			subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					Long.valueOf(500_000 + i));
			subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws" + i);

			SubscribeResult result = this.subscriptionRegistry
					.subscribe(subscribeMessage);

			ra.addSubscriber(matchPolicy[ix], topics[ix],
					result.getSubscription().getSubscriptionId(),
					Long.valueOf(500_000 + i));

			ixs.add(ix);
			UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(700_000 + i,
					result.getSubscription().getSubscriptionId());
			unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					Long.valueOf(500_000 + i));
			unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
					"ws" + i);
			unsubs.add(unsubscribeMessage);
		}

		StopWatch sw = new StopWatch();
		sw.start();
		for (int i = 0; i < 100_000; i++) {
			assertThat(this.subscriptionRegistry.getMatchSubscriptions("help"))
					.containsExactly(ra.subscriptionId(topics[0], matchPolicy[0]));
			assertThat(this.subscriptionRegistry.hasSubscribers("help")).isTrue();
			assertThat(this.subscriptionRegistry.getMatchSubscriptions("helpa"))
					.isEmpty();
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.foo.userevent")).containsExactly(
							ra.subscriptionId(topics[1], matchPolicy[1]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.bar.userevent")).containsExactly(
							ra.subscriptionId(topics[1], matchPolicy[1]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.a12.userevent")).containsExactly(
							ra.subscriptionId(topics[1], matchPolicy[1]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.foo.userevent.bar")).isEmpty();
			assertThat(
					this.subscriptionRegistry.getMatchSubscriptions("com.myapp.foo.user"))
							.isEmpty();
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp2.foo.userevent")).isEmpty();

			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.topic.emergency.11"))
							.containsExactly(
									ra.subscriptionId(topics[2], matchPolicy[2]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.topic.emergency-low"))
							.containsExactly(
									ra.subscriptionId(topics[2], matchPolicy[2]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.topic.emergency.category.severe"))
							.containsExactly(
									ra.subscriptionId(topics[2], matchPolicy[2]));
			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.topic.emergency")).containsExactly(
							ra.subscriptionId(topics[2], matchPolicy[2]));

			assertThat(this.subscriptionRegistry
					.getMatchSubscriptions("com.myapp.topic.emerge")).isEmpty();
		}
		sw.stop();
		System.out.println(sw.prettyPrint());

		int c = 0;
		for (Integer ix : ixs) {
			UnsubscribeMessage unsubMessage = unsubs.get(c);
			ra.removeSubscriber(matchPolicy[ix], topics[ix],
					unsubMessage.getSubscriptionId(), unsubMessage.getWampSessionId());
			this.subscriptionRegistry.unsubscribe(unsubMessage);
			c++;

			assertRegistry(ra);
		}

	}

	@Test
	public void testManyExactTopics() {
		RegistryAssert ra = new RegistryAssert();
		for (int i = 0; i < 10_000; i++) {

			SubscribeMessage subscribeMessage = new SubscribeMessage(i + 1, "topic." + i);
			subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					Long.valueOf(500_000 + i));
			subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws" + i);

			SubscribeResult result = this.subscriptionRegistry
					.subscribe(subscribeMessage);

			ra.addSubscriber(MatchPolicy.EXACT, "topic." + i,
					result.getSubscription().getSubscriptionId(),
					Long.valueOf(500_000 + i));
		}
		assertRegistry(ra);
	}

	@Test
	public void testRemoveWebSocketSessionOne() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "com.myapp.user",
				MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result1.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.user",
				result1.getSubscription().getSubscriptionId(), 123L);

		subscribeMessage = new SubscribeMessage(1, "com.myapp.test", MatchPolicy.EXACT);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result2.isCreated()).isTrue();
		ra.addSubscriber(MatchPolicy.EXACT, "com.myapp.test",
				result2.getSubscription().getSubscriptionId(), 123L);

		assertRegistry(ra);

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.user.error"))
						.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.test"))
				.containsExactly(result2.getSubscription().getSubscriptionId());

		List<UnsubscribeResult> results = this.subscriptionRegistry
				.removeWebSocketSessionId("one", 123L);
		assertThat(results).hasSize(2);
		assertThat(results.get(0).getError()).isNull();
		assertThat(results.get(1).getError()).isNull();
		assertThat(results.get(0).isDeleted()).isTrue();
		assertThat(results.get(1).isDeleted()).isTrue();

		ra = new RegistryAssert();
		assertRegistry(ra);

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.user.error"))
						.isEmpty();
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.test"))
				.isEmpty();
	}

	@Test
	public void testRemoveWebSocketSessionWithOthers() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "com.myapp.user",
				MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result1 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result1.isCreated()).isTrue();

		RegistryAssert ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.user",
				result1.getSubscription().getSubscriptionId(), 123L);

		subscribeMessage = new SubscribeMessage(2, "com.myapp.user", MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");

		SubscribeResult resultO = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(resultO.isCreated()).isFalse();

		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.user",
				resultO.getSubscription().getSubscriptionId(), 124L);

		subscribeMessage = new SubscribeMessage(3, "com.myapp.test", MatchPolicy.EXACT);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult result2 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(result2.isCreated()).isTrue();
		ra.addSubscriber(MatchPolicy.EXACT, "com.myapp.test",
				result2.getSubscription().getSubscriptionId(), 123L);

		subscribeMessage = new SubscribeMessage(4, "com.myapp.test", MatchPolicy.EXACT);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");

		SubscribeResult resultO2 = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(resultO2.isCreated()).isFalse();
		ra.addSubscriber(MatchPolicy.EXACT, "com.myapp.test",
				resultO2.getSubscription().getSubscriptionId(), 124L);

		assertRegistry(ra);

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.user.error"))
						.containsExactly(result1.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.test"))
				.containsExactly(result2.getSubscription().getSubscriptionId());

		List<UnsubscribeResult> results = this.subscriptionRegistry
				.removeWebSocketSessionId("one", 123L);
		assertThat(results).hasSize(2);
		assertThat(results.get(0).getError()).isNull();
		assertThat(results.get(1).getError()).isNull();
		assertThat(results.get(0).isDeleted()).isFalse();
		assertThat(results.get(1).isDeleted()).isFalse();

		ra = new RegistryAssert();
		ra.addSubscriber(MatchPolicy.PREFIX, "com.myapp.user",
				resultO.getSubscription().getSubscriptionId(), 124L);
		ra.addSubscriber(MatchPolicy.EXACT, "com.myapp.test",
				resultO2.getSubscription().getSubscriptionId(), 124L);
		assertRegistry(ra);

		assertThat(
				this.subscriptionRegistry.getMatchSubscriptions("com.myapp.user.error"))
						.containsExactly(resultO.getSubscription().getSubscriptionId());
		assertThat(this.subscriptionRegistry.getMatchSubscriptions("com.myapp.test"))
				.containsExactly(resultO2.getSubscription().getSubscriptionId());
	}

	@Test
	public void testSubscribeEventHandlers() throws NoSuchMethodException {
		InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
				new HandlerMethod(this, "testSubscribeEventHandlers"));
		EventListenerInfo eli = new EventListenerInfo(handlerMethod,
				new String[] { "one", "two", "three" }, MatchPolicy.EXACT);

		this.subscriptionRegistry.subscribeEventHandlers(Collections.singletonList(eli));

		Set<Subscription> oneSub = this.subscriptionRegistry.findSubscriptions("one");
		Set<Subscription> twoSub = this.subscriptionRegistry.findSubscriptions("two");
		Set<Subscription> threeSub = this.subscriptionRegistry.findSubscriptions("three");
		assertThat(oneSub).hasSize(1);
		assertThat(twoSub).hasSize(1);
		assertThat(threeSub).hasSize(1);

		assertThat(oneSub.iterator().next().getTopic()).isEqualTo("one");
		assertThat(twoSub.iterator().next().getTopic()).isEqualTo("two");
		assertThat(threeSub.iterator().next().getTopic()).isEqualTo("three");

		assertThat(oneSub.iterator().next().getSubscribers()).isEmpty();
		assertThat(twoSub.iterator().next().getSubscribers()).isEmpty();
		assertThat(threeSub.iterator().next().getSubscribers()).isEmpty();

		assertThat(oneSub.iterator().next().getEventListenerHandlerMethods())
				.containsExactly(handlerMethod);
		assertThat(twoSub.iterator().next().getEventListenerHandlerMethods())
				.containsExactly(handlerMethod);
		assertThat(threeSub.iterator().next().getEventListenerHandlerMethods())
				.containsExactly(handlerMethod);
	}

	@Test
	public void testSubscribeEventHandlersWithOthers() throws NoSuchMethodException {
		InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
				new HandlerMethod(this, "testSubscribeEventHandlers"));
		EventListenerInfo eli1 = new EventListenerInfo(handlerMethod,
				new String[] { "one.two" }, MatchPolicy.EXACT);
		EventListenerInfo eli2 = new EventListenerInfo(handlerMethod,
				new String[] { "one" }, MatchPolicy.PREFIX);

		this.subscriptionRegistry.subscribeEventHandlers(Arrays.asList(eli1, eli2));

		Set<Subscription> oneSub = this.subscriptionRegistry.findSubscriptions("one.two");
		assertThat(oneSub).hasSize(2);

		SubscribeMessage subscribeMessage = new SubscribeMessage(1, "one",
				MatchPolicy.PREFIX);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");

		SubscribeResult resultWs = this.subscriptionRegistry.subscribe(subscribeMessage);
		assertThat(resultWs.isCreated()).isFalse();

		oneSub = this.subscriptionRegistry.findSubscriptions("one.two");
		assertThat(oneSub).hasSize(2);
		Subscription sub = oneSub.stream().filter(s -> !s.getSubscribers().isEmpty())
				.findFirst().orElse(null);
		assertThat(sub.getEventListenerHandlerMethods()).containsExactly(handlerMethod);
		assertThat(sub.getSubscribers()).hasSize(1);
		sub = oneSub.stream().filter(s -> s.getSubscribers().isEmpty()).findFirst()
				.orElse(null);
		assertThat(sub.getEventListenerHandlerMethods()).containsExactly(handlerMethod);
	}

	private void assertRegistry(RegistryAssert ra) {
		EnumMap<MatchPolicy, List<Long>> subscriptions = this.subscriptionRegistry
				.listSubscriptions();
		assertThat(subscriptions).containsOnlyKeys(MatchPolicy.EXACT, MatchPolicy.PREFIX,
				MatchPolicy.WILDCARD);

		for (MatchPolicy policy : MatchPolicy.values()) {
			List<Long> subscriptionsId = ra.subscriptions(policy);
			assertThat(subscriptions.get(policy)).hasSameElementsAs(subscriptionsId);

			for (Long subscriptionId : subscriptionsId) {
				Collection<Long> subscribers = ra.subscribers(subscriptionId);
				assertThat(this.subscriptionRegistry.listSubscribers(subscriptionId))
						.hasSameElementsAs(subscribers);
				assertThat(this.subscriptionRegistry.countSubscribers(subscriptionId))
						.isEqualTo(subscribers.size());

				SubscriptionDetail detail = this.subscriptionRegistry
						.getSubscription(subscriptionId);
				assertThat(detail.getCreatedTimeMillis()).isGreaterThan(0);
				assertThat(detail.getId()).isEqualTo(subscriptionId);
				assertThat(detail.getMatchPolicy()).isEqualTo(policy);
				assertThat(detail.getTopic()).isEqualTo(ra.topic(subscriptionId));
			}

			assertThat(this.subscriptionRegistry.lookupSubscription("topic", policy))
					.isEqualTo(ra.subscriptionId("topic", policy));
		}

		ra.topics().forEach(topic -> {
			assertThat(this.subscriptionRegistry.hasSubscribers(topic)).isTrue();
		});
		assertThat(this.subscriptionRegistry.hasSubscribers("x")).isFalse();
	}

	private class RegistryAssert {

		Map<MatchPolicy, Map<String, Map<Long, Set<Long>>>> registry = new HashMap<>();

		RegistryAssert() {
			this.registry.put(MatchPolicy.EXACT, new HashMap<>());
			this.registry.put(MatchPolicy.WILDCARD, new HashMap<>());
			this.registry.put(MatchPolicy.PREFIX, new HashMap<>());
		}

		@Nullable
		public Long subscriptionId(String topic, MatchPolicy policy) {
			Map<Long, Set<Long>> map = this.registry.get(policy).get(topic);
			if (map != null) {
				return map.keySet().iterator().next();
			}
			return null;
		}

		void addSubscriber(MatchPolicy match, String topic, long subscriptionId,
				Long subscriberId) {
			Set<Long> subs = this.registry.computeIfAbsent(match, k -> new HashMap<>())
					.computeIfAbsent(topic, k -> new HashMap<>())
					.computeIfAbsent(subscriptionId, k -> new HashSet<>());
			if (subscriberId != null) {
				subs.add(subscriberId);
			}
		}

		void removeSubscriber(MatchPolicy match, String topic, long subscriptionId,
				Long subscriberId) {
			Set<Long> subs = this.registry.computeIfAbsent(match, k -> new HashMap<>())
					.computeIfAbsent(topic, k -> new HashMap<>())
					.computeIfAbsent(subscriptionId, k -> new HashSet<>());
			if (subscriberId != null) {
				subs.remove(subscriberId);
				if (subs.isEmpty()) {
					this.registry.computeIfAbsent(match, k -> new HashMap<>())
							.remove(topic);
				}
			}
		}

		@Nullable
		public String topic(Long subscriptionId) {
			for (MatchPolicy policy : MatchPolicy.values()) {
				Map<String, Map<Long, Set<Long>>> map = this.registry.get(policy);
				for (Entry<String, Map<Long, Set<Long>>> entry : map.entrySet()) {
					if (entry.getValue().containsKey(subscriptionId)) {
						return entry.getKey();
					}
				}
			}
			return null;
		}

		Collection<Long> subscribers(long subscriptionId) {
			return this.registry.values().stream().flatMap(m -> m.values().stream())
					.filter(m -> m.containsKey(subscriptionId)).findFirst()
					.map(m -> m.get(subscriptionId)).orElse(Collections.emptySet());

		}

		List<Long> subscriptions(MatchPolicy match) {
			return this.registry.get(match).values().stream()
					.flatMap(m -> m.keySet().stream()).collect(Collectors.toList());
		}

		List<String> topics() {
			return this.registry.values().stream().flatMap(m -> m.keySet().stream())
					.collect(Collectors.toList());
		}

	}
}
