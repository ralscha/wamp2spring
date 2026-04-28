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

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.authorization.WampAuthorizationDecision;
import ch.rasc.wamp2spring.authorization.WampAuthorizer;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.message.UnsubscribedMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.util.HandlerMethodService;

public class PubSubMessageHandlerTest {

	@Mock
	private SubscribableChannel clientInboundChannel;

	@Mock
	private SubscribableChannel brokerChannel;

	@Mock
	private MessageChannel clientOutboundChannel;

	@Mock
	private HandlerMethodService handlerMethodService;

	@Mock
	private EventStore eventStore;

	@Mock
	private ApplicationContext applicationContext;

	private PubSubMessageHandler pubSubMessageHandler;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);
		Mockito.when(this.clientOutboundChannel.send(ArgumentMatchers.any(WampMessage.class))).thenReturn(true);
		this.pubSubMessageHandler = new PubSubMessageHandler(this.clientInboundChannel, this.brokerChannel,
				this.clientOutboundChannel, new SubscriptionRegistry(), this.handlerMethodService, new Features(),
				this.eventStore);
		this.pubSubMessageHandler.setApplicationContext(this.applicationContext);
		this.pubSubMessageHandler.start();
	}

	@Test
	public void disclosePublisherAddsAuthDetailsToEvent() {
		subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_ADMIN"));
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic")
			.discloseMe()
			.addArgument("payload")
			.build();
		publishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");
		publishMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 100L);
		publishMessage.setHeader(WampMessageHeader.PRINCIPAL, new TestPrincipal("publisher", "ROLE_ADMIN"));

		this.pubSubMessageHandler.handleMessage(publishMessage);

		EventMessage eventMessage = captureSingleEvent();
		assertThat(eventMessage.getPublisher()).isEqualTo(100L);
		assertThat(eventMessage.getPublisherAuthId()).isEqualTo("publisher");
		assertThat(eventMessage.getPublisherAuthRole()).isEqualTo("ADMIN");
	}

	@Test
	public void storesEventHistoryForMatchingSubscriptions() {
		long subscriptionId = subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_ADMIN"));
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic").addArgument("payload").build();
		this.pubSubMessageHandler.handleMessage(publishMessage);

		Mockito.verify(this.eventStore)
			.storeHistoryEvent(ArgumentMatchers.eq(subscriptionId), ArgumentMatchers.anyLong(),
					ArgumentMatchers.anyLong(), ArgumentMatchers.same(publishMessage));
	}

	@Test
	public void eligibleAuthIdFiltersSubscribers() {
		subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_USER"));
		subscribe("ws-2", 2L, new TestPrincipal("bob", "ROLE_ADMIN"));
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic")
			.eligibleAuthIds(List.of("alice"))
			.addArgument("payload")
			.build();

		this.pubSubMessageHandler.handleMessage(publishMessage);

		EventMessage eventMessage = captureSingleEvent();
		assertThat(eventMessage.getWebSocketSessionId()).isEqualTo("ws-1");
	}

	@Test
	public void shardedSubscriptionRoutesEventsByRkey() {
		subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_USER"), false, "node-a", true);
		subscribe("ws-2", 2L, new TestPrincipal("bob", "ROLE_ADMIN"), false, "node-b", true);
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage firstPublishMessage = PublishMessage.builder(10L, "topic")
			.rkey("b")
			.addArgument("payload-1")
			.build();
		firstPublishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");
		firstPublishMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, List.of(publisherRole(true)));
		this.pubSubMessageHandler.handleMessage(firstPublishMessage);

		PublishMessage secondPublishMessage = PublishMessage.builder(11L, "topic")
			.rkey("a")
			.addArgument("payload-2")
			.build();
		secondPublishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");
		secondPublishMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, List.of(publisherRole(true)));
		this.pubSubMessageHandler.handleMessage(secondPublishMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(2)).send(messageCaptor.capture());
		assertThat(messageCaptor.getAllValues()).filteredOn(EventMessage.class::isInstance)
			.map(EventMessage.class::cast)
			.extracting(EventMessage::getWebSocketSessionId)
			.containsExactly("ws-1", "ws-2");
	}

	@Test
	public void excludeAuthRoleFiltersSubscribers() {
		subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_USER"));
		subscribe("ws-2", 2L, new TestPrincipal("bob", "ROLE_ADMIN"));
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic")
			.excludeAuthRoles(List.of("USER"))
			.addArgument("payload")
			.build();

		this.pubSubMessageHandler.handleMessage(publishMessage);

		EventMessage eventMessage = captureSingleEvent();
		assertThat(eventMessage.getWebSocketSessionId()).isEqualTo("ws-2");
	}

	@Test
	public void revocationSendsExtendedUnsubscribedOnlyToSupportingSubscribers() {
		long subscriptionId = subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_USER"), true);
		subscribe("ws-2", 2L, new TestPrincipal("bob", "ROLE_USER"), false);
		Mockito.clearInvocations(this.clientOutboundChannel);

		int revoked = this.pubSubMessageHandler.revokeSubscription(subscriptionId, "no.longer.authorized");

		assertThat(revoked).isEqualTo(2);
		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(UnsubscribedMessage.class);

		UnsubscribedMessage unsubscribedMessage = (UnsubscribedMessage) messageCaptor.getValue();
		assertThat(unsubscribedMessage.getRequestId()).isZero();
		assertThat(unsubscribedMessage.getSubscriptionId()).isEqualTo(subscriptionId);
		assertThat(unsubscribedMessage.getReason()).isEqualTo("no.longer.authorized");
		assertThat(unsubscribedMessage.getWebSocketSessionId()).isEqualTo("ws-1");
	}

	@Test
	public void subscribeInvalidTopicUriIsRejected() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "topic with space");
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws-1");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 1L);

		this.pubSubMessageHandler.handleMessage(subscribeMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(ErrorMessage.class);
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.INVALID_URI.getExternalValue());
	}

	@Test
	public void publishReservedTopicUriIsRejected() {
		PublishMessage publishMessage = PublishMessage.builder(10L, "wamp.topic").addArgument("payload").build();
		publishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");

		this.pubSubMessageHandler.handleMessage(publishMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(ErrorMessage.class);
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.INVALID_URI.getExternalValue());
	}

	@Test
	public void internalPublishReservedTopicUriIsAllowed() {
		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "wamp.topic");
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws-1");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 1L);

		this.pubSubMessageHandler.handleMessage(subscribeMessage);
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "wamp.topic").addArgument("payload").build();

		this.pubSubMessageHandler.handleMessage(publishMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(EventMessage.class);
		assertThat(((EventMessage) messageCaptor.getValue()).getWebSocketSessionId()).isEqualTo("ws-1");
	}

	@Test
	public void deniedSubscribeReturnsAuthorizationError() {
		Mockito.when(this.applicationContext.getBeansOfType(WampAuthorizer.class))
			.thenReturn(Map.of("denySubscribe", context -> WampAuthorizationDecision.deny(WampError.NOT_AUTHORIZED)));

		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "topic");
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws-1");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 1L);

		this.pubSubMessageHandler.handleMessage(subscribeMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.NOT_AUTHORIZED.getExternalValue());
	}

	@Test
	public void deniedPublishReturnsAuthorizationError() {
		Mockito.when(this.applicationContext.getBeansOfType(WampAuthorizer.class))
			.thenReturn(
					Map.of("denyPublish", context -> WampAuthorizationDecision.deny(WampError.AUTHORIZATION_FAILED)));

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic").addArgument("payload").build();
		publishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");

		this.pubSubMessageHandler.handleMessage(publishMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.AUTHORIZATION_FAILED.getExternalValue());
	}

	@Test
	public void shardedSubscriptionRequiresBrokerFeature() {
		Features features = new Features();
		features.disable(ch.rasc.wamp2spring.config.Feature.BROKER_SHARDED_SUBSCRIPTION);
		this.pubSubMessageHandler = new PubSubMessageHandler(this.clientInboundChannel, this.brokerChannel,
				this.clientOutboundChannel, new SubscriptionRegistry(), this.handlerMethodService, features,
				this.eventStore);
		this.pubSubMessageHandler.setApplicationContext(this.applicationContext);
		this.pubSubMessageHandler.start();

		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "topic", MatchPolicy.EXACT, false, "node-a", null);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "ws-1");
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 1L);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, List.of(subscriberRole(false, true)));

		this.pubSubMessageHandler.handleMessage(subscribeMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(ErrorMessage.class);
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void shardedPublishRequiresPublisherFeature() {
		subscribe("ws-1", 1L, new TestPrincipal("alice", "ROLE_USER"), false, "node-a", true);
		Mockito.clearInvocations(this.clientOutboundChannel);

		PublishMessage publishMessage = PublishMessage.builder(10L, "topic").rkey("a").addArgument("payload").build();
		publishMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "pub-ws");
		publishMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, List.of(publisherRole(false)));

		this.pubSubMessageHandler.handleMessage(publishMessage);

		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		assertThat(messageCaptor.getValue()).isInstanceOf(ErrorMessage.class);
		assertThat(((ErrorMessage) messageCaptor.getValue()).getError())
			.isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	private long subscribe(String webSocketSessionId, long wampSessionId, Principal principal) {
		return subscribe(webSocketSessionId, wampSessionId, principal, false);
	}

	private long subscribe(String webSocketSessionId, long wampSessionId, Principal principal,
			boolean subscriptionRevocationSupported) {
		return subscribe(webSocketSessionId, wampSessionId, principal, subscriptionRevocationSupported, null, false);
	}

	private long subscribe(String webSocketSessionId, long wampSessionId, Principal principal,
			boolean subscriptionRevocationSupported, @Nullable String nkey, boolean shardedSubscriptionSupported) {
		SubscribeMessage subscribeMessage = new SubscribeMessage(wampSessionId, "topic", MatchPolicy.EXACT, false, nkey,
				null);
		subscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		subscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		subscribeMessage.setHeader(WampMessageHeader.PRINCIPAL, principal);
		if (subscriptionRevocationSupported || shardedSubscriptionSupported) {
			WampRole subscriberRole = subscriberRole(subscriptionRevocationSupported, shardedSubscriptionSupported);
			subscribeMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, List.of(subscriberRole));
		}
		this.pubSubMessageHandler.handleMessage(subscribeMessage);
		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.atLeastOnce()).send(messageCaptor.capture());
		long subscriptionId = messageCaptor.getAllValues()
			.stream()
			.filter(SubscribedMessage.class::isInstance)
			.map(SubscribedMessage.class::cast)
			.map(SubscribedMessage::getSubscriptionId)
			.findFirst()
			.orElseThrow();
		Mockito.clearInvocations(this.clientOutboundChannel);
		return subscriptionId;
	}

	private static WampRole subscriberRole(boolean subscriptionRevocationSupported,
			boolean shardedSubscriptionSupported) {
		WampRole subscriberRole = new WampRole("subscriber");
		if (subscriptionRevocationSupported) {
			subscriberRole.addFeature("subscription_revocation");
		}
		if (shardedSubscriptionSupported) {
			subscriberRole.addFeature("sharded_subscription");
		}
		return subscriberRole;
	}

	private static WampRole publisherRole(boolean shardedSubscriptionSupported) {
		WampRole publisherRole = new WampRole("publisher");
		if (shardedSubscriptionSupported) {
			publisherRole.addFeature("sharded_subscription");
		}
		return publisherRole;
	}

	private EventMessage captureSingleEvent() {
		ArgumentCaptor<WampMessage> eventCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(eventCaptor.capture());
		return eventCaptor.getAllValues()
			.stream()
			.filter(EventMessage.class::isInstance)
			.map(EventMessage.class::cast)
			.findFirst()
			.orElseThrow();
	}

	private static final class TestPrincipal implements Principal {

		private final String name;

		private final List<TestAuthority> authorities;

		private TestPrincipal(String name, String... authorities) {
			this.name = name;
			this.authorities = java.util.Arrays.stream(authorities).map(TestAuthority::new).toList();
		}

		@Override
		public String getName() {
			return this.name;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public List<TestAuthority> getAuthorities() {
			return this.authorities;
		}

	}

	private static final class TestAuthority {

		private final String authority;

		private TestAuthority(String authority) {
			this.authority = authority;
		}

		@SuppressWarnings({ "UnusedMethod", "EffectivelyPrivate" })
		public String getAuthority() {
			return this.authority;
		}

	}

}