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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

import ch.rasc.wamp2spring.annotation.WampListener;
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
import ch.rasc.wamp2spring.util.HandlerMethodService;
import ch.rasc.wamp2spring.util.IdGenerator;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

public class PubSubMessageHandler implements MessageHandler, SmartLifecycle,
		InitializingBean, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private final SubscribableChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private final SubscriptionRegistry subscriptionRegistry;

	private boolean autoStartup = true;

	private volatile boolean running = false;

	private final Object lifecycleMonitor = new Object();

	private ApplicationContext applicationContext;

	private final HandlerMethodService handlerMethodService;

	public PubSubMessageHandler(SubscribableChannel clientInboundChannel,
			MessageChannel clientOutboundChannel,
			SubscriptionRegistry subscriptionRegistry,
			HandlerMethodService handlerMethodService) {
		this.clientInboundChannel = clientInboundChannel;
		this.clientOutboundChannel = clientOutboundChannel;
		this.subscriptionRegistry = subscriptionRegistry;
		this.handlerMethodService = handlerMethodService;
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
			this.running = true;
		}
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			this.clientInboundChannel.unsubscribe(this);
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

		if (message instanceof SubscribeMessage) {
			SubscribeMessage subscribeMessage = (SubscribeMessage) message;
			SubscribeResult result = this.subscriptionRegistry
					.subscribe(subscribeMessage);
			sendMessageToClient(new SubscribedMessage(subscribeMessage,
					result.getSubscription().getSubscriptionId()));

			sendSubscriptionEvents(result, subscribeMessage);
		}
		else if (message instanceof UnsubscribeMessage) {
			UnsubscribeMessage unsubscribeMessage = (UnsubscribeMessage) message;

			UnsubscribeResult result = this.subscriptionRegistry
					.unsubscribe(unsubscribeMessage);
			if (result.getError() == null) {
				sendMessageToClient(new UnsubscribedMessage(unsubscribeMessage));
				sendSubscriptionEvents(result, unsubscribeMessage);
			}
			else {
				sendMessageToClient(
						new ErrorMessage(unsubscribeMessage, result.getError()));
			}

		}
		else if (message instanceof PublishMessage) {
			PublishMessage publishMessage = (PublishMessage) message;
			long publicationId = IdGenerator.newRandomId(null);
			handlePublishMessage(publishMessage, publicationId);

			if (publishMessage.isAcknowledge()) {
				sendMessageToClient(new PublishedMessage(publishMessage, publicationId));
			}
		}

	}

	@EventListener
	void handleDisconnectEvent(WampDisconnectEvent event) {
		List<UnsubscribeResult> results = this.subscriptionRegistry
				.removeWebSocketSessionId(event.getWebSocketSessionId(),
						event.getWampSessionId());

		for (UnsubscribeResult result : results) {
			sendSubscriptionEvents(result, event);
		}
	}

	private void sendSubscriptionEvents(SubscribeResult result,
			SubscribeMessage subscribeMessage) {
		SubscriptionDetail detail = new SubscriptionDetail(result.getSubscription());

		if (result.isCreated()) {
			this.applicationContext.publishEvent(
					new WampSubscriptionCreatedEvent(subscribeMessage, detail));
		}

		this.applicationContext.publishEvent(
				new WampSubscriptionSubscribedEvent(subscribeMessage, detail));

	}

	private void sendSubscriptionEvents(UnsubscribeResult result,
			UnsubscribeMessage unsubscribeMessage) {
		SubscriptionDetail detail = new SubscriptionDetail(result.getSubscription());
		this.applicationContext.publishEvent(
				new WampSubscriptionUnsubscribedEvent(unsubscribeMessage, detail));

		if (result.isDeleted()) {
			this.applicationContext.publishEvent(
					new WampSubscriptionDeletedEvent(unsubscribeMessage, detail));
		}
	}

	private void sendSubscriptionEvents(UnsubscribeResult result,
			WampDisconnectEvent event) {
		SubscriptionDetail detail = new SubscriptionDetail(result.getSubscription());
		this.applicationContext
				.publishEvent(new WampSubscriptionUnsubscribedEvent(event, detail));

		if (result.isDeleted()) {
			this.applicationContext
					.publishEvent(new WampSubscriptionDeletedEvent(event, detail));
		}
	}

	private void handlePublishMessage(PublishMessage publishMessage, long publicationId) {
		Set<Subscription> subscriptions = this.subscriptionRegistry
				.findSubscriptions(publishMessage.getTopic());

		if (subscriptions.size() > 0) {
			Long publisher = null;

			for (Subscription subscription : subscriptions) {
				String topic = null;
				if (subscription.getMatchPolicy() != MatchPolicy.EXACT) {
					topic = publishMessage.getTopic();
				}
				if (publishMessage.isDiscloseMe()) {
					publisher = publishMessage.getWampSessionId();
				}

				for (Subscriber subscriber : subscription.getSubscribers()) {
					if (isEligible(publishMessage, subscriber)) {
						EventMessage eventMessage = new EventMessage(
								subscriber.getWebSocketSessionId(),
								subscription.getSubscriptionId(), publicationId, topic,
								publisher, publishMessage);
						sendMessageToClient(eventMessage);
					}
				}

				// do not send event messages to annotated methods when the publish
				// message was created from the WampPublisher and exclude me is set
				// to true
				if (publishMessage.getWebSocketSessionId() == null
						&& publishMessage.isExcludeMe()) {
					continue;
				}

				List<InvocableHandlerMethod> eventListenerHandlerMethods = subscription
						.getEventListenerHandlerMethods();
				if (eventListenerHandlerMethods != null) {

					EventMessage eventMessage = new EventMessage(null, -1, publicationId,
							publishMessage.getTopic(), null, publishMessage);

					for (InvocableHandlerMethod handlerMethod : eventListenerHandlerMethods) {
						try {
							this.handlerMethodService.invoke(eventMessage, handlerMethod);
						}
						catch (Exception e) {
							if (this.logger.isErrorEnabled()) {
								this.logger.error(
										"Error while invoking event message handler method "
												+ handlerMethod,
										e);
							}
						}
					}
				}
			}
		}
	}

	private static boolean isEligible(PublishMessage publishMessage,
			Subscriber subscriber) {

		String myWebSocketSessionId = publishMessage.getWebSocketSessionId();

		if (publishMessage.isExcludeMe() && myWebSocketSessionId != null
				&& myWebSocketSessionId.equals(subscriber.getWebSocketSessionId())) {
			return false;
		}

		if (publishMessage.getEligible() != null && !publishMessage.getEligible()
				.contains(subscriber.getWampSessionId())) {
			return false;
		}

		if (publishMessage.getExclude() != null
				&& publishMessage.getExclude().contains(subscriber.getWampSessionId())) {
			return false;
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
		for (String beanName : this.applicationContext
				.getBeanNamesForType(Object.class)) {
			detectAnnotatedMethods(beanName);
		}
	}

	private void detectAnnotatedMethods(String beanName) {
		Class<?> handlerType = this.applicationContext.getType(beanName);
		final Class<?> userType = ClassUtils.getUserClass(handlerType);

		List<EventListenerInfo> eventListeners = detectEventListeners(beanName, userType);
		this.subscriptionRegistry.subscribeEventHandlers(eventListeners);
	}

	private List<EventListenerInfo> detectEventListeners(String beanName,
			Class<?> userType) {

		List<EventListenerInfo> registry = new ArrayList<>();

		Set<Method> methods = MethodIntrospector.selectMethods(userType,
				(MethodFilter) method -> AnnotationUtils.findAnnotation(method,
						WampListener.class) != null);

		for (Method method : methods) {
			WampListener wampEventListenerAnnotation = AnnotationUtils
					.findAnnotation(method, WampListener.class);

			InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
					new HandlerMethod(this.applicationContext.getBean(beanName), method));

			String[] topics = (String[]) AnnotationUtils
					.getValue(wampEventListenerAnnotation);
			if (topics.length == 0) {
				// by default use beanName.methodName as topic
				topics = new String[] { beanName + "." + method.getName() };
			}

			MatchPolicy match = (MatchPolicy) AnnotationUtils
					.getValue(wampEventListenerAnnotation, "match");
			EventListenerInfo info = new EventListenerInfo(handlerMethod, topics, match);
			registry.add(info);
		}

		return registry;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

}
