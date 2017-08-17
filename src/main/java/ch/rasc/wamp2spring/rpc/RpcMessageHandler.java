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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampProcedureRegisteredEvent;
import ch.rasc.wamp2spring.event.WampProcedureUnregisteredEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.RegisteredMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.UnregisteredMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.util.HandlerMethodService;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

import org.springframework.util.StringUtils;

public class RpcMessageHandler implements MessageHandler, SmartLifecycle,
		InitializingBean, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private final SubscribableChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private boolean autoStartup = true;

	private volatile boolean running = false;

	private final Object lifecycleMonitor = new Object();

	private ApplicationContext applicationContext;

	private final ProcedureRegistry procedureRegistry;

	private final Map<String, InvocableHandlerMethod> wampMethods = new ConcurrentHashMap<>();

	private final HandlerMethodService handlerMethodService;

	public RpcMessageHandler(SubscribableChannel clientInboundChannel,
			MessageChannel clientOutboundChannel, ProcedureRegistry procedureRegistry,
			HandlerMethodService handlerMethodService) {
		this.clientInboundChannel = clientInboundChannel;
		this.clientOutboundChannel = clientOutboundChannel;
		this.procedureRegistry = procedureRegistry;
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

		if (message instanceof RegisterMessage) {
			RegisterMessage registerMessage = (RegisterMessage) message;
			long registration = this.procedureRegistry.register(registerMessage);
			if (registration != -1) {
				sendMessageToClient(new RegisteredMessage(registerMessage, registration));

				this.applicationContext
						.publishEvent(new WampProcedureRegisteredEvent(registerMessage));

			}
			else {
				sendMessageToClient(new ErrorMessage(registerMessage,
						WampError.PROCEDURE_ALREADY_EXISTS));
			}
		}
		else if (message instanceof UnregisterMessage) {
			UnregisterMessage unregisterMessage = (UnregisterMessage) message;
			UnregisterResult result = this.procedureRegistry
					.unregister(unregisterMessage);
			if (result.isSuccess()) {
				sendMessageToClient(new UnregisteredMessage(unregisterMessage));

				this.applicationContext.publishEvent(new WampProcedureUnregisteredEvent(
						unregisterMessage, result.getProcedure()));

				for (ErrorMessage errorMessage : result.getInvocationErrors()) {
					handleErrorMessage(errorMessage);
				}
			}
			else {
				sendMessageToClient(new ErrorMessage(unregisterMessage,
						WampError.NO_SUCH_REGISTRATION));
			}
		}
		else if (message instanceof CallMessage) {
			CallMessage callMessage = (CallMessage) message;
			InvocableHandlerMethod handlerMethod = this.wampMethods
					.get(callMessage.getProcedure());
			if (handlerMethod != null) {
				callWampMethod(callMessage, handlerMethod);
			}
			else {
				WampMessage errorOrInvocationMessage = this.procedureRegistry
						.createInvocationMessage(callMessage);

				try {
					this.clientOutboundChannel.send(errorOrInvocationMessage);
				}
				catch (Throwable ex) {
					if (errorOrInvocationMessage instanceof InvocationMessage) {
						sendMessageToClient(
								new ErrorMessage(callMessage, WampError.NETWORK_FAILURE));
					}
				}
			}
		}
		else if (message instanceof YieldMessage) {
			YieldMessage yieldMessage = (YieldMessage) message;
			CallMessage callMessage = this.procedureRegistry
					.removeInvocationCall(yieldMessage);
			if (callMessage != null) {
				ResultMessage resultMessage = new ResultMessage(yieldMessage,
						callMessage);
				sendMessageToClient(resultMessage);
			}
		}
		else if (message instanceof ErrorMessage) {
			handleErrorMessage((ErrorMessage) message);
		}

	}

	@EventListener
	void handleDisconnectEvent(WampDisconnectEvent event) {
		List<UnregisterResult> unregisterResults = this.procedureRegistry
				.unregisterWebSocketSession(event.getWebSocketSessionId());

		for (UnregisterResult unregisterResult : unregisterResults) {

			this.applicationContext.publishEvent(new WampProcedureUnregisteredEvent(event,
					unregisterResult.getProcedure()));

			for (ErrorMessage errorMessage : unregisterResult.getInvocationErrors()) {
				handleErrorMessage(errorMessage);
			}
		}
	}

	private void handleErrorMessage(ErrorMessage errorMessage) {
		CallMessage callMessage = this.procedureRegistry
				.removeInvocationCall(errorMessage);
		if (callMessage != null) {
			ErrorMessage calErrorMessage = new ErrorMessage(errorMessage, callMessage);
			sendMessageToClient(calErrorMessage);
		}
	}

	private void callWampMethod(CallMessage callMessage,
			InvocableHandlerMethod handlerMethod) {
		try {
			Object returnValue = this.handlerMethodService.invoke(callMessage,
					handlerMethod);

			ResultMessage resultMessage;
			if (returnValue instanceof WampResult) {
				WampResult wampResult = (WampResult) returnValue;
				resultMessage = new ResultMessage(callMessage, wampResult.getResults(),
						wampResult.getResultsKw());
			}
			else {
				resultMessage = new ResultMessage(callMessage,
						returnValue != null ? Collections.singletonList(returnValue)
								: null,
						null);
			}

			sendMessageToClient(resultMessage);
		}
		catch (Exception e) {
			sendMessageToClient(
					new ErrorMessage(callMessage, WampError.INVALID_ARGUMENT));

			if (this.logger.isErrorEnabled()) {
				this.logger.error(
						"Error while invoking the handlerMethod " + handlerMethod, e);
			}
		}
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
			Class<?> handlerType = this.applicationContext.getType(beanName);
			final Class<?> userType = ClassUtils.getUserClass(handlerType);

			detectWampMethods(beanName, userType);
		}
	}

	private void detectWampMethods(String beanName, Class<?> userType) {

		Set<Method> methods = MethodIntrospector.selectMethods(userType,
				(MethodFilter) method -> AnnotationUtils.findAnnotation(method,
						WampProcedure.class) != null);

		for (Method method : methods) {
			WampProcedure annotation = AnnotationUtils.findAnnotation(method,
					WampProcedure.class);

			InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
					new HandlerMethod(this.applicationContext.getBean(beanName), method));

			String procedure = (String) AnnotationUtils.getValue(annotation);
			if (!StringUtils.hasText(procedure)) {
				procedure = beanName + "." + method.getName();
			}

			this.wampMethods.put(procedure, handlerMethod);

			if (this.logger.isInfoEnabled()) {
				this.logger.info("Mapped \"" + procedure + "\" onto " + handlerMethod);
			}
		}

	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

}
