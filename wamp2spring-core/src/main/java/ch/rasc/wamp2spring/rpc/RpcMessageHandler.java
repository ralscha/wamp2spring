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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.util.StringUtils;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampProcedureRegisteredEvent;
import ch.rasc.wamp2spring.event.WampProcedureUnregisteredEvent;
import ch.rasc.wamp2spring.event.WampRegistrationCreatedEvent;
import ch.rasc.wamp2spring.event.WampRegistrationDeletedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.CancelMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InterruptMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.RegisteredMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.UnregisteredMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.util.HandlerMethodService;
import ch.rasc.wamp2spring.util.InvocableHandlerMethod;

public class RpcMessageHandler implements MessageHandler, SmartLifecycle, InitializingBean, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private final SubscribableChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private boolean autoStartup = true;

	private volatile boolean running = false;

	private final Object lifecycleMonitor = new Object();

	@Nullable private ApplicationContext applicationContext;

	private final ProcedureRegistry procedureRegistry;

	private final Map<String, InvocableHandlerMethod> wampMethods = new ConcurrentHashMap<>();

	private final HandlerMethodService handlerMethodService;

	private final Features features;

	private final Map<Long, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

	private final Set<Long> cancelOnTimeoutInvocations = ConcurrentHashMap.newKeySet();

	private final ScheduledThreadPoolExecutor callTimeoutExecutor;

	public RpcMessageHandler(SubscribableChannel clientInboundChannel, MessageChannel clientOutboundChannel,
			ProcedureRegistry procedureRegistry, HandlerMethodService handlerMethodService, Features features) {
		this.clientInboundChannel = clientInboundChannel;
		this.clientOutboundChannel = clientOutboundChannel;
		this.procedureRegistry = procedureRegistry;
		this.handlerMethodService = handlerMethodService;
		this.features = features;
		this.callTimeoutExecutor = new ScheduledThreadPoolExecutor(1, new CallTimeoutThreadFactory());
		this.callTimeoutExecutor.setRemoveOnCancelPolicy(true);
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
			cancelAllTimeoutTasks();
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

		if (message instanceof RegisterMessage registerMessage) {
			if (registerMessage.getMatchPolicy() != MatchPolicy.EXACT
					&& this.features.isDisabled(Feature.DEALER_PATTERN_BASED_REGISTRATION)) {
				sendMessageToClient(new ErrorMessage(registerMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}
			if (registerMessage.getInvokePolicy() != InvocationPolicy.SINGLE
					&& this.features.isDisabled(Feature.DEALER_SHARED_REGISTRATION)) {
				sendMessageToClient(new ErrorMessage(registerMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}
			RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
			if (registerResult.isSuccess()) {
				sendMessageToClient(new RegisteredMessage(registerMessage, registerResult.getRegistrationId()));

				if (registerResult.isCreated()) {
					getApplicationContext().publishEvent(
							new WampRegistrationCreatedEvent(registerMessage, registerResult.getRegistrationId()));
				}

				getApplicationContext().publishEvent(
						new WampProcedureRegisteredEvent(registerMessage, registerResult.getRegistrationId()));
			}
			else {
				sendMessageToClient(new ErrorMessage(registerMessage, WampError.PROCEDURE_ALREADY_EXISTS));
			}
		}
		else if (message instanceof UnregisterMessage unregisterMessage) {
			UnregisterResult result = this.procedureRegistry.unregister(unregisterMessage);
			if (result.isSuccess()) {
				sendMessageToClient(new UnregisteredMessage(unregisterMessage));
				String procedure = Objects.requireNonNull(result.getProcedure());

				getApplicationContext().publishEvent(
						new WampProcedureUnregisteredEvent(unregisterMessage, procedure, result.getRegistrationId()));
				if (result.isDeleted()) {
					getApplicationContext().publishEvent(
							new WampRegistrationDeletedEvent(unregisterMessage, procedure, result.getRegistrationId()));
				}

				List<ErrorMessage> invocationErrors = result.getInvocationErrors();
				if (invocationErrors != null) {
					for (ErrorMessage errorMessage : invocationErrors) {
						handleErrorMessage(errorMessage);
					}
				}
			}
			else {
				sendMessageToClient(new ErrorMessage(unregisterMessage, WampError.NO_SUCH_REGISTRATION));
			}
		}
		else if (message instanceof CallMessage callMessage) {

			if (callMessage.isDiscloseMe() && this.features.isDisabled(Feature.DEALER_CALLER_IDENTIFICATION)) {
				sendMessageToClient(new ErrorMessage(callMessage, WampError.DISCLOSE_ME_DISALLOWED));
				return;
			}

			Long timeout = callMessage.getTimeout();
			if (timeout != null && (!this.features.isEnabled(Feature.DEALER_CALL_TIMEOUT)
					|| !callerSupportsFeature(callMessage, Feature.DEALER_CALL_TIMEOUT) || timeout <= 0L)) {
				sendMessageToClient(new ErrorMessage(callMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}

			if (callMessage.isReceiveProgress() && (!this.features.isEnabled(Feature.DEALER_PROGRESSIVE_CALL_RESULTS)
					|| !callerSupportsFeature(callMessage, Feature.DEALER_PROGRESSIVE_CALL_RESULTS))) {
				sendMessageToClient(new ErrorMessage(callMessage, WampError.OPTION_NOT_ALLOWED));
				return;
			}

			InvocableHandlerMethod handlerMethod = this.wampMethods.get(callMessage.getProcedure());
			if (handlerMethod != null) {
				callWampMethod(callMessage, handlerMethod);
			}
			else {
				WampMessage errorOrInvocationMessage = this.procedureRegistry.createInvocationMessage(callMessage);

				try {
					this.clientOutboundChannel.send(errorOrInvocationMessage);
					if (errorOrInvocationMessage instanceof InvocationMessage invocationMessage) {
						scheduleCallTimeout(callMessage, invocationMessage);
					}
				}
				catch (Throwable ex) {
					if (errorOrInvocationMessage instanceof InvocationMessage) {
						sendMessageToClient(new ErrorMessage(callMessage, WampError.NETWORK_FAILURE));
					}
				}
			}
		}
		else if (message instanceof CancelMessage cancelMessage) {
			handleCancelMessage(cancelMessage);
		}
		else if (message instanceof YieldMessage yieldMessage) {
			ProcedureRegistry.PendingCall pendingCall = yieldMessage.isProgress()
					? this.procedureRegistry.getPendingInvocation(yieldMessage.getRequestId())
					: this.procedureRegistry.removePendingInvocation(yieldMessage.getRequestId());
			if (pendingCall != null) {
				if (yieldMessage.isProgress()) {
					scheduleCallTimeout(pendingCall.getCallMessage(), pendingCall.getInvocationRequestId());
				}
				else {
					cancelTimeoutTask(pendingCall.getInvocationRequestId());
				}
				ResultMessage resultMessage = new ResultMessage(yieldMessage, pendingCall.getCallMessage());
				sendMessageToClient(resultMessage);
			}
		}
		else if (message instanceof ErrorMessage errorMessage) {
			handleErrorMessage(errorMessage);
		}

	}

	public int revokeRegistration(long registrationId, @Nullable String reason) {
		List<UnregisterResult> unregisterResults = this.procedureRegistry.revokeRegistration(registrationId);
		for (UnregisterResult unregisterResult : unregisterResults) {
			Procedure procedure = unregisterResult.getProcedureObject();
			if (procedure == null) {
				continue;
			}

			if (this.features.isEnabled(Feature.DEALER_REGISTRATION_REVOCATION)
					&& procedure.isRegistrationRevocationSupported()) {
				UnregisteredMessage unregisteredMessage = new UnregisteredMessage(0, registrationId, reason);
				unregisteredMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
						procedure.getWebSocketSessionId());
				unregisteredMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, procedure.getWampSessionId());
				sendMessageToClient(unregisteredMessage);
			}
			String procedureUri = Objects.requireNonNull(unregisterResult.getProcedure());
			Long wampSessionId = Objects.requireNonNull(procedure.getWampSessionId());

			getApplicationContext().publishEvent(new WampProcedureUnregisteredEvent(
					new WampDisconnectEvent(wampSessionId, procedure.getWebSocketSessionId(), null), procedureUri,
					unregisterResult.getRegistrationId()));
			if (unregisterResult.isDeleted()) {
				getApplicationContext().publishEvent(new WampRegistrationDeletedEvent(
						new WampDisconnectEvent(wampSessionId, procedure.getWebSocketSessionId(), null), procedureUri,
						unregisterResult.getRegistrationId()));
			}

			List<ErrorMessage> invocationErrors = unregisterResult.getInvocationErrors();
			if (invocationErrors != null) {
				for (ErrorMessage errorMessage : invocationErrors) {
					handleErrorMessage(errorMessage);
				}
			}
		}
		return unregisterResults.size();
	}

	private void handleCancelMessage(CancelMessage cancelMessage) {
		String requestedMode = cancelMessage.getModeOrDefault();
		if (isAdvancedCancelMode(requestedMode) && (!this.features.isEnabled(Feature.DEALER_CALL_CANCELING)
				|| !callerSupportsFeature(cancelMessage, Feature.DEALER_CALL_CANCELING))) {
			sendMessageToClient(new ErrorMessage(cancelMessage, WampError.OPTION_NOT_ALLOWED));
			return;
		}

		if (!isSupportedCancelMode(requestedMode)) {
			sendMessageToClient(new ErrorMessage(cancelMessage, WampError.OPTION_NOT_ALLOWED));
			return;
		}

		ProcedureRegistry.PendingCall pendingCall = this.procedureRegistry.getPendingCall(cancelMessage);
		if (pendingCall == null) {
			return;
		}

		String mode = requestedMode;
		if (!pendingCall.getProcedure().isCallCancelingSupported()) {
			mode = CancelMessage.MODE_SKIP;
		}

		if (CancelMessage.MODE_SKIP.equals(mode)) {
			this.procedureRegistry.removePendingCall(pendingCall);
			cancelTimeoutTask(pendingCall.getInvocationRequestId());
			sendMessageToClient(new ErrorMessage(pendingCall.getCallMessage(), WampError.CANCELED));
			return;
		}

		InterruptMessage interruptMessage = new InterruptMessage(pendingCall.getInvocationRequestId(), mode,
				pendingCall.getProcedure().getWebSocketSessionId());
		try {
			this.clientOutboundChannel.send(interruptMessage);
		}
		catch (Throwable ex) {
			this.logger.error("Failed to send " + interruptMessage, ex);
		}

		if (CancelMessage.MODE_KILL.equals(mode)) {
			this.cancelOnTimeoutInvocations.add(pendingCall.getInvocationRequestId());
		}

		if (CancelMessage.MODE_KILLNOWAIT.equals(mode)) {
			this.procedureRegistry.removePendingCall(pendingCall);
			cancelTimeoutTask(pendingCall.getInvocationRequestId());
			sendMessageToClient(new ErrorMessage(pendingCall.getCallMessage(), WampError.CANCELED));
		}
	}

	@EventListener
	void handleDisconnectEvent(WampDisconnectEvent event) {
		List<UnregisterResult> unregisterResults = this.procedureRegistry
			.unregisterWebSocketSession(event.getWebSocketSessionId());

		for (UnregisterResult unregisterResult : unregisterResults) {
			String procedure = Objects.requireNonNull(unregisterResult.getProcedure());

			getApplicationContext().publishEvent(
					new WampProcedureUnregisteredEvent(event, procedure, unregisterResult.getRegistrationId()));
			if (unregisterResult.isDeleted()) {
				getApplicationContext().publishEvent(
						new WampRegistrationDeletedEvent(event, procedure, unregisterResult.getRegistrationId()));
			}

			List<ErrorMessage> invocationErrors = unregisterResult.getInvocationErrors();
			if (invocationErrors != null) {
				for (ErrorMessage errorMessage : invocationErrors) {
					handleErrorMessage(errorMessage);
				}
			}
		}

		List<ProcedureRegistry.PendingCall> pendingCalls = this.procedureRegistry
			.removePendingCalls(event.getWebSocketSessionId());
		for (ProcedureRegistry.PendingCall pendingCall : pendingCalls) {
			cancelTimeoutTask(pendingCall.getInvocationRequestId());
			if (pendingCall.getProcedure().isCallCancelingSupported()) {
				InterruptMessage interruptMessage = new InterruptMessage(pendingCall.getInvocationRequestId(),
						CancelMessage.MODE_KILLNOWAIT, pendingCall.getProcedure().getWebSocketSessionId());
				try {
					this.clientOutboundChannel.send(interruptMessage);
				}
				catch (Throwable ex) {
					this.logger.error("Failed to send " + interruptMessage, ex);
				}
			}
		}
	}

	private void handleErrorMessage(ErrorMessage errorMessage) {
		cancelTimeoutTask(errorMessage.getRequestId());
		CallMessage callMessage = this.procedureRegistry.removeInvocationCall(errorMessage);
		if (callMessage != null) {
			ErrorMessage calErrorMessage = new ErrorMessage(errorMessage, callMessage);
			sendMessageToClient(calErrorMessage);
		}
	}

	@SuppressWarnings("unchecked")
	private void callWampMethod(CallMessage callMessage, InvocableHandlerMethod handlerMethod) {
		try {
			Object returnValue = this.handlerMethodService.invoke(callMessage, handlerMethod);

			List<Object> arguments = null;
			Map<String, Object> argumentsKw = null;

			if (returnValue instanceof WampResult wampResult) {
				arguments = wampResult.getResults();
				argumentsKw = wampResult.getResultsKw();
			}
			else if (returnValue instanceof List) {
				arguments = (List) returnValue;
			}
			else if (returnValue instanceof Map) {
				argumentsKw = (Map) returnValue;
			}
			else if (returnValue != null) {
				arguments = Collections.singletonList(returnValue);
			}

			ResultMessage resultMessage = new ResultMessage(callMessage, arguments, argumentsKw);
			sendMessageToClient(resultMessage);
		}
		catch (WampException e) {
			sendMessageToClient(new ErrorMessage(callMessage, e.getUri(), e.getArguments(), e.getArgumentsKw()));

			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Error while invoking the handlerMethod " + handlerMethod, e);
			}
		}
		catch (Exception e) {
			if ("org.springframework.security.access.AccessDeniedException".equals(e.getClass().getName())) {
				sendMessageToClient(new ErrorMessage(callMessage, WampError.NOT_AUTHORIZED));
			}
			else {
				sendMessageToClient(new ErrorMessage(callMessage, WampError.INVALID_ARGUMENT));
			}

			if (this.logger.isErrorEnabled()) {
				this.logger.error("Error while invoking the handlerMethod " + handlerMethod, e);
			}
		}
	}

	private void scheduleCallTimeout(CallMessage callMessage, InvocationMessage invocationMessage) {
		scheduleCallTimeout(callMessage, invocationMessage.getRequestId());
	}

	private void scheduleCallTimeout(CallMessage callMessage, long invocationRequestId) {
		Long timeout = callMessage.getTimeout();
		if (timeout == null || timeout <= 0L) {
			return;
		}

		// The dealer remains the source of truth for timeout enforcement even when the
		// callee did not advertise timeout support and therefore only receives the call.
		ScheduledFuture<?> timeoutTask = this.callTimeoutExecutor.schedule(() -> handleCallTimeout(invocationRequestId),
				timeout, TimeUnit.MILLISECONDS);
		ScheduledFuture<?> previous = this.timeoutTasks.put(invocationRequestId, timeoutTask);
		if (previous != null) {
			previous.cancel(false);
		}
	}

	private void handleCallTimeout(long invocationRequestId) {
		this.timeoutTasks.remove(invocationRequestId);
		boolean canceledByCaller = this.cancelOnTimeoutInvocations.remove(invocationRequestId);
		ProcedureRegistry.PendingCall pendingCall = this.procedureRegistry.removePendingInvocation(invocationRequestId);
		if (pendingCall == null) {
			return;
		}

		if (!canceledByCaller && pendingCall.getProcedure().isCallCancelingSupported()) {
			InterruptMessage interruptMessage = new InterruptMessage(invocationRequestId, CancelMessage.MODE_KILLNOWAIT,
					pendingCall.getProcedure().getWebSocketSessionId());
			try {
				this.clientOutboundChannel.send(interruptMessage);
			}
			catch (Throwable ex) {
				this.logger.error("Failed to send " + interruptMessage, ex);
			}
		}

		sendMessageToClient(new ErrorMessage(pendingCall.getCallMessage(),
				canceledByCaller ? WampError.CANCELED : WampError.TIMEOUT));
	}

	private void cancelTimeoutTask(long invocationRequestId) {
		this.cancelOnTimeoutInvocations.remove(invocationRequestId);
		ScheduledFuture<?> timeoutTask = this.timeoutTasks.remove(invocationRequestId);
		if (timeoutTask != null) {
			timeoutTask.cancel(false);
		}
	}

	private void cancelAllTimeoutTasks() {
		for (Map.Entry<Long, ScheduledFuture<?>> entry : this.timeoutTasks.entrySet()) {
			entry.getValue().cancel(false);
		}
		this.timeoutTasks.clear();
		this.cancelOnTimeoutInvocations.clear();
	}

	private static boolean isAdvancedCancelMode(String mode) {
		return CancelMessage.MODE_KILL.equals(mode) || CancelMessage.MODE_KILLNOWAIT.equals(mode);
	}

	private static boolean isSupportedCancelMode(String mode) {
		return CancelMessage.MODE_SKIP.equals(mode) || CancelMessage.MODE_KILL.equals(mode)
				|| CancelMessage.MODE_KILLNOWAIT.equals(mode);
	}

	private static boolean callerSupportsFeature(WampMessage message, Feature feature) {
		List<WampRole> peerRoles = message.getPeerRoles();
		if (peerRoles == null) {
			return false;
		}

		for (WampRole role : peerRoles) {
			if ("caller".equals(role.getRole()) && role.hasFeature(feature.getExternalValue())) {
				return true;
			}
		}

		return false;
	}

	private static final class CallTimeoutThreadFactory implements ThreadFactory {

		private final AtomicInteger threadCounter = new AtomicInteger();

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "wampRpcTimeout-" + this.threadCounter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
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
		ApplicationContext context = getApplicationContext();
		for (String beanName : context.getBeanNamesForType(Object.class)) {
			Class<?> handlerType = context.getType(beanName);
			if (handlerType != null) {
				final Class<?> userType = ClassUtils.getUserClass(handlerType);
				detectWampMethods(beanName, userType);
			}
		}
	}

	private void detectWampMethods(String beanName, Class<?> userType) {

		Set<Method> methods = MethodIntrospector.selectMethods(userType,
				(MethodFilter) method -> AnnotationUtils.findAnnotation(method, WampProcedure.class) != null);

		for (Method method : methods) {
			WampProcedure annotation = Objects
				.requireNonNull(AnnotationUtils.findAnnotation(method, WampProcedure.class));

			InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
					new HandlerMethod(getApplicationContext().getBean(beanName), method));

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
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	private ApplicationContext getApplicationContext() {
		return Objects.requireNonNull(this.applicationContext);
	}

}
