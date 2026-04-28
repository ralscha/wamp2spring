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
package ch.rasc.wamp2spring.rpc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.event.WampSubscriptionCreatedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionDeletedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionSubscribedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionUnsubscribedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;

public class SubscriptionMetaApiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void exposesSubscriptionMetaProcedures() throws WampException {
		SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();
		long ordersExact = subscribe(subscriptionRegistry, 1L, 101L, "ws-1", "com.myapp.orders", MatchPolicy.EXACT);
		subscribe(subscriptionRegistry, 2L, 202L, "ws-2", "com.myapp.orders", MatchPolicy.EXACT);
		long ordersPrefix = subscribe(subscriptionRegistry, 3L, 303L, "ws-3", "com.myapp.orders", MatchPolicy.PREFIX);

		SubscriptionMetaApi api = new SubscriptionMetaApi(subscriptionRegistry,
				new WampPublisher(Mockito.mock(MessageChannel.class)));

		WampResult listResult = api.list();
		WampResult lookupResult = api.lookup(new CallMessage(10L, SubscriptionMetaApi.LOOKUP,
				List.of("com.myapp.orders", Map.of("match", "exact"))));
		WampResult matchResult = api
			.match(new CallMessage(11L, SubscriptionMetaApi.MATCH, List.of("com.myapp.orders.us.created")));
		WampResult getResult = api.get(new CallMessage(12L, SubscriptionMetaApi.GET, List.of(ordersExact)));
		WampResult listSubscribersResult = api
			.listSubscribers(new CallMessage(13L, SubscriptionMetaApi.LIST_SUBSCRIBERS, List.of(ordersExact)));
		WampResult countSubscribersResult = api
			.countSubscribers(new CallMessage(14L, SubscriptionMetaApi.COUNT_SUBSCRIBERS, List.of(ordersExact)));

		Map<String, List<Long>> listedSubscriptions = (Map<String, List<Long>>) Objects
			.requireNonNull(listResult.getResults())
			.get(0);
		assertThat(listedSubscriptions.get("exact")).containsExactly(ordersExact);
		assertThat(listedSubscriptions.get("prefix")).containsExactly(ordersPrefix);
		assertThat(Objects.requireNonNull(lookupResult.getResults())).containsExactly(ordersExact);
		assertThat((List<Long>) Objects.requireNonNull(matchResult.getResults()).get(0)).containsExactly(ordersPrefix);

		Map<String, Object> detail = (Map<String, Object>) Objects.requireNonNull(getResult.getResults()).get(0);
		assertThat(detail.get("id")).isEqualTo(ordersExact);
		assertThat(detail.get("uri")).isEqualTo("com.myapp.orders");
		assertThat(detail.get("match")).isEqualTo(MatchPolicy.EXACT.getExternalValue());
		assertThat(detail.get("created")).isInstanceOf(String.class);
		assertThat((List<Long>) Objects.requireNonNull(listSubscribersResult.getResults()).get(0))
			.containsExactlyInAnyOrder(101L, 202L);
		assertThat(Objects.requireNonNull(countSubscribersResult.getResults())).containsExactly(2);

		assertThatThrownBy(() -> api.get(new CallMessage(15L, SubscriptionMetaApi.GET, List.of(999L))))
			.isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.NO_SUCH_SUBSCRIPTION.getExternalValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void publishesSubscriptionLifecycleTopics() {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(brokerChannel.send(ArgumentMatchers.any(PublishMessage.class))).thenReturn(true);

		SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();
		SubscribeMessage subscribeMessage = subscribeMessage(1L, 101L, "ws-1", "com.myapp.orders", MatchPolicy.EXACT);
		long subscriptionId = subscribe(subscriptionRegistry, subscribeMessage);
		SubscriptionDetail detail = subscriptionRegistry.getSubscription(subscriptionId);
		assertThat(detail).isNotNull();
		SubscriptionDetail requiredDetail = Objects.requireNonNull(detail);

		SubscriptionMetaApi api = new SubscriptionMetaApi(subscriptionRegistry, new WampPublisher(brokerChannel));
		api.onSubscriptionCreated(new WampSubscriptionCreatedEvent(subscribeMessage, requiredDetail));
		api.onSubscriptionSubscribed(new WampSubscriptionSubscribedEvent(subscribeMessage, requiredDetail));

		UnsubscribeMessage unsubscribeMessage = unsubscribeMessage(2L, subscriptionId, "ws-1", 101L);
		api.onSubscriptionUnsubscribed(new WampSubscriptionUnsubscribedEvent(unsubscribeMessage, requiredDetail));
		api.onSubscriptionDeleted(new WampSubscriptionDeletedEvent(unsubscribeMessage, requiredDetail));

		ArgumentCaptor<PublishMessage> publishCaptor = ArgumentCaptor.forClass(PublishMessage.class);
		Mockito.verify(brokerChannel, Mockito.times(4)).send(publishCaptor.capture());
		List<PublishMessage> publishedMessages = publishCaptor.getAllValues();
		assertThat(publishedMessages.get(0).getTopic()).isEqualTo(SubscriptionMetaApi.ON_CREATE);
		assertThat(publishedMessages.get(1).getTopic()).isEqualTo(SubscriptionMetaApi.ON_SUBSCRIBE);
		assertThat(publishedMessages.get(2).getTopic()).isEqualTo(SubscriptionMetaApi.ON_UNSUBSCRIBE);
		assertThat(publishedMessages.get(3).getTopic()).isEqualTo(SubscriptionMetaApi.ON_DELETE);

		List<Object> createArguments = Objects.requireNonNull(publishedMessages.get(0).getArguments());
		assertThat(createArguments.get(0)).isEqualTo(101L);
		assertThat(((Map<String, Object>) createArguments.get(1)).get("id")).isEqualTo(subscriptionId);
		assertThat(publishedMessages.get(1).getArguments()).containsExactly(101L, subscriptionId);
		assertThat(publishedMessages.get(2).getArguments()).containsExactly(101L, subscriptionId);
		assertThat(publishedMessages.get(3).getArguments()).containsExactly(101L, subscriptionId);
	}

	private static long subscribe(SubscriptionRegistry subscriptionRegistry, long requestId, long wampSessionId,
			String webSocketSessionId, String topic, MatchPolicy matchPolicy) {
		return subscribe(subscriptionRegistry,
				subscribeMessage(requestId, wampSessionId, webSocketSessionId, topic, matchPolicy));
	}

	private static long subscribe(SubscriptionRegistry subscriptionRegistry, SubscribeMessage message) {
		try {
			Method subscribeMethod = SubscriptionRegistry.class.getDeclaredMethod("subscribe", SubscribeMessage.class);
			subscribeMethod.setAccessible(true);
			Object subscribeResult = subscribeMethod.invoke(subscriptionRegistry, message);

			Method getSubscriptionMethod = subscribeResult.getClass().getDeclaredMethod("getSubscription");
			getSubscriptionMethod.setAccessible(true);
			Object subscription = getSubscriptionMethod.invoke(subscribeResult);

			Method getSubscriptionIdMethod = subscription.getClass().getDeclaredMethod("getSubscriptionId");
			getSubscriptionIdMethod.setAccessible(true);
			return (Long) getSubscriptionIdMethod.invoke(subscription);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static SubscribeMessage subscribeMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String topic, MatchPolicy matchPolicy) {
		SubscribeMessage message = new SubscribeMessage(requestId, topic, matchPolicy, false);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

	private static UnsubscribeMessage unsubscribeMessage(long requestId, long subscriptionId, String webSocketSessionId,
			long wampSessionId) {
		UnsubscribeMessage message = new UnsubscribeMessage(requestId, subscriptionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		return message;
	}

}