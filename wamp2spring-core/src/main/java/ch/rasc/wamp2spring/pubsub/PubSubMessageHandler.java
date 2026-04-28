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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.annotation.WampListener;
import ch.rasc.wamp2spring.authorization.WampAuthorizationAction;
import ch.rasc.wamp2spring.authorization.WampAuthorizationContext;
import ch.rasc.wamp2spring.authorization.WampAuthorizationDecision;
import ch.rasc.wamp2spring.authorization.WampAuthorizer;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionCreatedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionDeletedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionSubscribedEvent;
import ch.rasc.wamp2spring.event.WampSubscriptionUnsubscribedEvent;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.message.UnsubscribeMessage;
import ch.rasc.wamp2spring.message.UnsubscribedMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.util.HandlerMethodService;
import ch.rasc.wamp2spring.util.IdGenerator;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;
import ch.rasc.wamp2spring.util.WampUriValidator;

public class PubSubMessageHandler implements MessageHandler, SmartLifecycle, InitializingBean, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private final SubscribableChannel clientInboundChannel;

	private final SubscribableChannel brokerChannel;

	private final MessageChannel clientOutboundChannel;

	private final SubscriptionRegistry subscriptionRegistry;

	private boolean autoStartup = true;

	private volatile boolean running = false;

	private final Object lifecycleMonitor = new Object();

	@Nullable private ApplicationContext applicationContext;

	private final HandlerMethodService handlerMethodService;

	private final Features features;

	private final EventStore eventStore;

	public PubSubMessageHandler(SubscribableChannel clientInboundChannel, SubscribableChannel brokerChannel,
			MessageChannel clientOutboundChannel, SubscriptionRegistry subscriptionRegistry,
			HandlerMethodService handlerMethodService, Features features, EventStore eventStore) {
		this.clientInboundChannel = clientInboundChannel;
		this.brokerChannel = brokerChannel;
		this.clientOutboundChannel = clientOutboundChannel;
		this.subscriptionRegistry = subscriptionRegistry;
		this.handlerMethodService = handlerMethodService;
		this.features = features;
		this.eventStore = eventStore;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			this.clientInboundChannel.subscribe(this);
			this.brokerChannel.subscribe(this);
			this.running = true;
		}
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			this.clientInboundChannel.unsubscribe(this);
			this.brokerChannel.unsubscribe(this);
			this.running = false;
		}
	}

	@Override
	public final void stop(Runnable callback) {
		synchronized (this.lifecycleMonitor) {
			stop();
			callback.run();
		}
	}

	@Override
	public final boolean isRunning() {
		synchronized (this.lifecycleMonitor) {
			return this.running;
		}
	}

	@Override
	public void handleMessage(Message<?> message) {
		if (!this.running) {
			if (this.logger.isTraceEnabled()) {
				this.logger.trace(this + " not running yet. Ignoring " + message);
			}
			return;
		}

		if (message instanceof SubscribeMessage subscribeMessage) {
			if (!validateUri(() -> WampUriValidator.validateSubscriptionTopic(subscribeMessage.getTopic(),
					subscribeMessage.getMatchPolicy()), subscribeMessage)) {
				return;
			}
			if (!authorize(subscribeMessage, WampAuthorizationAction.SUBSCRIBE, subscribeMessage.getTopic(),
					subscribeMessage.getMatchPolicy())) {
				return;
			}

			if (this.features.isDisabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)
					&& subscribeMessage.getMatchPolicy() != MatchPolicy.EXACT) {
				sendMessageToClient(new ErrorMessage(subscribeMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}
			if (subscribeMessage.getNkey() != null && (!this.features.isEnabled(Feature.BROKER_SHARDED_SUBSCRIPTION)
					|| !subscriberSupportsFeature(subscribeMessage, Feature.BROKER_SHARDED_SUBSCRIPTION))) {
				sendMessageToClient(new ErrorMessage(subscribeMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}

			SubscribeResult result = this.subscriptionRegistry.subscribe(subscribeMessage);
			sendMessageToClient(new SubscribedMessage(subscribeMessage, result.getSubscription().getSubscriptionId()));

			sendSubscriptionEvents(result, subscribeMessage);

			if (subscribeMessage.isGetRetained()) {
				handleRetentionRequest(subscribeMessage, result.getSubscription());
			}
		}
		else if (message instanceof UnsubscribeMessage unsubscribeMessage) {

			UnsubscribeResult result = this.subscriptionRegistry.unsubscribe(unsubscribeMessage);
			if (result.getError() == null) {
				sendMessageToClient(new UnsubscribedMessage(unsubscribeMessage));
				sendSubscriptionEvents(result, unsubscribeMessage);
			}
			else {
				sendMessageToClient(new ErrorMessage(unsubscribeMessage, result.getError()));
			}

		}
		else if (message instanceof PublishMessage publishMessage) {
			if (!validateUri(() -> validatePublishUri(publishMessage), publishMessage)) {
				return;
			}
			if (!authorize(publishMessage, WampAuthorizationAction.PUBLISH, publishMessage.getTopic(),
					MatchPolicy.EXACT)) {
				return;
			}
			if (publishMessage.isDiscloseMe() && this.features.isDisabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)) {
				if (publishMessage.getWebSocketSessionId() != null) {
					sendMessageToClient(new ErrorMessage(publishMessage, WampError.DISCLOSE_ME_DISALLOWED));
				}
				return;
			}
			if (publishMessage.getRkey() != null && (!this.features.isEnabled(Feature.BROKER_SHARDED_SUBSCRIPTION)
					|| !publisherSupportsFeature(publishMessage, Feature.BROKER_SHARDED_SUBSCRIPTION))) {
				sendMessageToClient(new ErrorMessage(publishMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}
			long publicationId = IdGenerator.newRandomId(null);
			handlePublishMessage(publishMessage, publicationId);

			if (publishMessage.isAcknowledge()) {
				sendMessageToClient(new PublishedMessage(publishMessage, publicationId));
			}

			if (this.features.isEnabled(Feature.BROKER_EVENT_RETENTION) && publishMessage.isRetain()) {
				this.eventStore.retain(publishMessage);
			}
		}

	}

	private static void validatePublishUri(PublishMessage publishMessage) throws WampException {
		if (publishMessage.getWebSocketSessionId() == null && publishMessage.getWampSessionId() == null) {
			WampUriValidator.validateExactUri(publishMessage.getTopic());
			return;
		}

		WampUriValidator.validatePublishTopic(publishMessage.getTopic());
	}

	public int revokeSubscription(long subscriptionId, @Nullable String reason) {
		List<SubscriptionRevocation> revocations = this.subscriptionRegistry.revokeSubscription(subscriptionId);
		for (SubscriptionRevocation revocation : revocations) {
			Subscriber subscriber = revocation.getSubscriber();
			if (subscriber.isSubscriptionRevocationSupported()) {
				UnsubscribedMessage unsubscribedMessage = new UnsubscribedMessage(0, subscriptionId, reason);
				unsubscribedMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
						subscriber.getWebSocketSessionId());
				unsubscribedMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, subscriber.getWampSessionId());
				sendMessageToClient(unsubscribedMessage);
			}

			sendSubscriptionEvents(revocation);
		}
		return revocations.size();
	}

	private void handleRetentionRequest(SubscribeMessage subscribeMessage, Subscription subscription) {

		List<PublishMessage> retainedMessages = this.eventStore.getRetained(subscription.getTopicMatch());

		if (!retainedMessages.isEmpty()) {
			Subscriber subscriber = new Subscriber(Objects.requireNonNull(subscribeMessage.getWebSocketSessionId()),
					Objects.requireNonNull(subscribeMessage.getWampSessionId()));
			for (PublishMessage retainedMessage : retainedMessages) {
				if (this.subscriptionRegistry.findSubscriptions(retainedMessage, retainedMessage.getTopic())
					.contains(subscription)) {
					publishRetentionEvent(subscription, subscriber, retainedMessage);
				}
			}
		}
	}

	private void publishRetentionEvent(Subscription subscription, Subscriber subscriber,
			PublishMessage publishMessage) {
		String topic = null;
		Long publisher = null;
		if (subscription.getMatchPolicy() != MatchPolicy.EXACT) {
			topic = publishMessage.getTopic();
		}
		if (publishMessage.isDiscloseMe()) {
			publisher = publishMessage.getWampSessionId();
		}

		if (isEligible(publishMessage, subscriber)) {
			EventMessage eventMessage = new EventMessage(subscriber.getWebSocketSessionId(),
					subscription.getSubscriptionId(), IdGenerator.newRandomId(null), topic, publisher, true,
					publishMessage);
			sendMessageToClient(eventMessage);
		}
	}

	private boolean validateUri(UriValidation validation, WampMessage message) {
		try {
			validation.validate();
			return true;
		}
		catch (WampException ex) {
			if (message instanceof SubscribeMessage subscribeMessage) {
				sendMessageToClient(new ErrorMessage(subscribeMessage, WampError.INVALID_URI));
			}
			else if (message instanceof PublishMessage publishMessage) {
				sendMessageToClient(new ErrorMessage(publishMessage, WampError.INVALID_URI));
			}
			return false;
		}
	}

	private boolean authorize(WampMessage message, WampAuthorizationAction action, String uri,
			MatchPolicy matchPolicy) {
		for (WampAuthorizer authorizer : getAuthorizers()) {
			WampAuthorizationDecision decision = authorizer
				.authorize(new WampAuthorizationContext(action, message, uri, matchPolicy));
			if (!decision.isGranted()) {
				sendAuthorizationError(message, decision.getError());
				return false;
			}
		}
		return true;
	}

	private static boolean publisherSupportsFeature(WampMessage message, Feature feature) {
		List<WampRole> peerRoles = message.getPeerRoles();
		if (peerRoles == null) {
			return false;
		}

		for (WampRole role : peerRoles) {
			if ("publisher".equals(role.getRole()) && role.hasFeature(feature.getExternalValue())) {
				return true;
			}
		}

		return false;
	}

	private static boolean subscriberSupportsFeature(WampMessage message, Feature feature) {
		List<WampRole> peerRoles = message.getPeerRoles();
		if (peerRoles == null) {
			return false;
		}

		for (WampRole role : peerRoles) {
			if ("subscriber".equals(role.getRole()) && role.hasFeature(feature.getExternalValue())) {
				return true;
			}
		}

		return false;
	}

	private Collection<WampAuthorizer> getAuthorizers() {
		if (this.applicationContext == null) {
			return List.of();
		}
		Map<String, WampAuthorizer> beans = this.applicationContext.getBeansOfType(WampAuthorizer.class);
		return beans != null ? beans.values() : List.of();
	}

	private void sendAuthorizationError(WampMessage message, WampError error) {
		if (message instanceof SubscribeMessage subscribeMessage) {
			sendMessageToClient(new ErrorMessage(subscribeMessage, error));
		}
		else if (message instanceof PublishMessage publishMessage) {
			sendMessageToClient(new ErrorMessage(publishMessage, error));
		}
	}

	@EventListener
	void handleDisconnectEvent(WampDisconnectEvent event) {
		List<UnsubscribeResult> results = this.subscriptionRegistry
			.removeWebSocketSessionId(event.getWebSocketSessionId(), event.getWampSessionId());

		for (UnsubscribeResult result : results) {
			sendSubscriptionEvents(result, event);
		}
	}

	private void sendSubscriptionEvents(SubscribeResult result, SubscribeMessage subscribeMessage) {
		SubscriptionDetail detail = new SubscriptionDetail(result.getSubscription());

		if (result.isCreated()) {
			getApplicationContext().publishEvent(new WampSubscriptionCreatedEvent(subscribeMessage, detail));
		}

		getApplicationContext().publishEvent(new WampSubscriptionSubscribedEvent(subscribeMessage, detail));

	}

	private void sendSubscriptionEvents(UnsubscribeResult result, UnsubscribeMessage unsubscribeMessage) {
		SubscriptionDetail detail = new SubscriptionDetail(Objects.requireNonNull(result.getSubscription()));
		getApplicationContext().publishEvent(new WampSubscriptionUnsubscribedEvent(unsubscribeMessage, detail));

		if (result.isDeleted()) {
			this.eventStore.deleteHistory(detail.getId());
			getApplicationContext().publishEvent(new WampSubscriptionDeletedEvent(unsubscribeMessage, detail));
		}
	}

	private void sendSubscriptionEvents(UnsubscribeResult result, WampDisconnectEvent event) {
		SubscriptionDetail detail = new SubscriptionDetail(Objects.requireNonNull(result.getSubscription()));
		getApplicationContext().publishEvent(new WampSubscriptionUnsubscribedEvent(event, detail));

		if (result.isDeleted()) {
			this.eventStore.deleteHistory(detail.getId());
			getApplicationContext().publishEvent(new WampSubscriptionDeletedEvent(event, detail));
		}
	}

	private void sendSubscriptionEvents(SubscriptionRevocation revocation) {
		SubscriptionDetail detail = new SubscriptionDetail(revocation.getSubscription());
		Subscriber subscriber = revocation.getSubscriber();
		UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(0,
				revocation.getSubscription().getSubscriptionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, subscriber.getWebSocketSessionId());
		unsubscribeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, subscriber.getWampSessionId());
		getApplicationContext().publishEvent(new WampSubscriptionUnsubscribedEvent(unsubscribeMessage, detail));

		if (revocation.isDeleted()) {
			this.eventStore.deleteHistory(detail.getId());
			getApplicationContext().publishEvent(new WampSubscriptionDeletedEvent(unsubscribeMessage, detail));
		}
	}

	private void handlePublishMessage(PublishMessage publishMessage, long publicationId) {
		Set<Subscription> subscriptions = this.subscriptionRegistry.findSubscriptions(publishMessage,
				publishMessage.getTopic());

		if (subscriptions != null && !subscriptions.isEmpty()) {
			long publicationTimestampMillis = System.currentTimeMillis();
			Long publisher = null;

			for (Subscription subscription : subscriptions) {
				if (this.features.isEnabled(Feature.BROKER_EVENT_HISTORY)) {
					this.eventStore.storeHistoryEvent(subscription.getSubscriptionId(), publicationId,
							publicationTimestampMillis, publishMessage);
				}

				String topic = null;
				if (subscription.getMatchPolicy() != MatchPolicy.EXACT) {
					topic = publishMessage.getTopic();
				}
				if (publishMessage.isDiscloseMe()) {
					publisher = publishMessage.getWampSessionId();
				}

				for (Subscriber subscriber : subscription.getSubscribers()) {
					if (isEligible(publishMessage, subscriber)) {
						EventMessage eventMessage = new EventMessage(subscriber.getWebSocketSessionId(),
								subscription.getSubscriptionId(), publicationId, topic, publisher, false,
								publishMessage);
						sendMessageToClient(eventMessage);
					}
				}

				// do not send event messages to annotated methods when the publish
				// message was created from the WampPublisher and exclude me is set
				// to true
				if (publishMessage.getWebSocketSessionId() == null && (publishMessage.isExcludeMe()
						|| this.features.isDisabled(Feature.BROKER_PUBLISHER_EXCLUSION))) {
					continue;
				}

				List<InvocableHandlerMethod> eventListenerHandlerMethods = subscription
					.getEventListenerHandlerMethods();
				if (eventListenerHandlerMethods != null) {

					EventMessage eventMessage = new EventMessage(null, -1, publicationId, topic, publisher, false,
							publishMessage);

					for (InvocableHandlerMethod handlerMethod : eventListenerHandlerMethods) {
						try {
							this.handlerMethodService.invoke(eventMessage, handlerMethod);
						}
						catch (Exception e) {
							if (this.logger.isErrorEnabled()) {
								this.logger.error("Error while invoking event message handler method " + handlerMethod,
										e);
							}
						}
					}
				}
			}
		}
	}

	@FunctionalInterface
	private interface UriValidation {

		void validate() throws WampException;

	}

	private boolean isEligible(PublishMessage publishMessage, Subscriber subscriber) {

		String myWebSocketSessionId = publishMessage.getWebSocketSessionId();

		if ((publishMessage.isExcludeMe() || this.features.isDisabled(Feature.BROKER_PUBLISHER_EXCLUSION))
				&& myWebSocketSessionId != null && myWebSocketSessionId.equals(subscriber.getWebSocketSessionId())) {
			return false;
		}

		if (this.features.isEnabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING)) {
			Set<Number> eligible = publishMessage.getEligible();
			if (eligible != null && !eligible.contains(subscriber.getWampSessionId())) {
				return false;
			}

			Set<String> eligibleAuthIds = publishMessage.getEligibleAuthIds();
			if (eligibleAuthIds != null
					&& (subscriber.getAuthId() == null || !eligibleAuthIds.contains(subscriber.getAuthId()))) {
				return false;
			}

			Set<String> eligibleAuthRoles = publishMessage.getEligibleAuthRoles();
			if (eligibleAuthRoles != null
					&& (subscriber.getAuthRole() == null || !eligibleAuthRoles.contains(subscriber.getAuthRole()))) {
				return false;
			}

			Set<Number> exclude = publishMessage.getExclude();
			if (exclude != null && exclude.contains(subscriber.getWampSessionId())) {
				return false;
			}

			Set<String> excludeAuthIds = publishMessage.getExcludeAuthIds();
			if (excludeAuthIds != null && subscriber.getAuthId() != null
					&& excludeAuthIds.contains(subscriber.getAuthId())) {
				return false;
			}

			Set<String> excludeAuthRoles = publishMessage.getExcludeAuthRoles();
			if (excludeAuthRoles != null && subscriber.getAuthRole() != null
					&& excludeAuthRoles.contains(subscriber.getAuthRole())) {
				return false;
			}
		}

		return true;
	}

	protected void sendMessageToClient(Message<?> message) {
		try {
			this.clientOutboundChannel.send(message);
		}
		catch (Throwable ex) {
			this.logger.error("Failed to send " + message, ex);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		for (String beanName : getApplicationContext().getBeanNamesForType(Object.class)) {
			detectAnnotatedMethods(beanName);
		}
	}

	private void detectAnnotatedMethods(String beanName) {
		ApplicationContext context = getApplicationContext();
		Class<?> handlerType = context.getType(beanName);
		if (handlerType == null) {
			return;
		}
		final Class<?> userType = ClassUtils.getUserClass(handlerType);

		List<EventListenerInfo> eventListeners = detectEventListeners(beanName, userType);
		this.subscriptionRegistry.subscribeEventHandlers(eventListeners);
	}

	private List<EventListenerInfo> detectEventListeners(String beanName, Class<?> userType) {

		List<EventListenerInfo> registry = new ArrayList<>();

		Set<Method> methods = MethodIntrospector.selectMethods(userType,
				(MethodFilter) method -> AnnotationUtils.findAnnotation(method, WampListener.class) != null);

		for (Method method : methods) {
			WampListener wampEventListenerAnnotation = Objects
				.requireNonNull(AnnotationUtils.findAnnotation(method, WampListener.class));

			InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
					new HandlerMethod(getApplicationContext().getBean(beanName), method));

			String[] topics = (String[]) Objects.requireNonNull(AnnotationUtils.getValue(wampEventListenerAnnotation));
			if (topics.length == 0) {
				// by default use beanName.methodName as topic
				topics = new String[] { beanName + "." + method.getName() };
			}

			MatchPolicy match = (MatchPolicy) Objects
				.requireNonNull(AnnotationUtils.getValue(wampEventListenerAnnotation, "match"));
			EventListenerInfo info = new EventListenerInfo(handlerMethod, topics, match);
			registry.add(info);
		}

		return registry;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	private ApplicationContext getApplicationContext() {
		return Objects.requireNonNull(this.applicationContext);
	}

}
