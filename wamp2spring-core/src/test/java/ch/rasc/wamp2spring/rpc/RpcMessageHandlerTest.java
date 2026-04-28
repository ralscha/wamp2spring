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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.CancelMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InterruptMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.RegisteredMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.UnregisteredMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.util.HandlerMethodService;

public class RpcMessageHandlerTest {

	@Mock
	private SubscribableChannel clientInboundChannel;

	@Mock
	private MessageChannel clientOutboundChannel;

	@Mock
	private HandlerMethodService handlerMethodService;

	@Mock
	private ApplicationContext applicationContext;

	private RpcMessageHandler rpcMessageHandler;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);
		Mockito.when(this.clientOutboundChannel.send(ArgumentMatchers.any(WampMessage.class))).thenReturn(true);

		Features features = new Features();
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(features);
		this.rpcMessageHandler = new RpcMessageHandler(this.clientInboundChannel, this.clientOutboundChannel,
				procedureRegistry, this.handlerMethodService, features);
		this.rpcMessageHandler.setApplicationContext(this.applicationContext);
		this.rpcMessageHandler.start();
	}

	@Test
	public void cancelKillNoWaitSendsInterruptAndCancelsCaller() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 100L);
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILLNOWAIT);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		ArgumentCaptor<WampMessage> postCancelCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(2)).send(postCancelCaptor.capture());
		List<WampMessage> sentMessages = postCancelCaptor.getAllValues();
		assertThat(sentMessages).hasSize(2);

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.filter(message -> message.getRequestId() == 10L)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.CANCELED.getExternalValue());
		assertThat(errorMessage.getWebSocketSessionId()).isEqualTo("caller-ws");

		InterruptMessage interruptMessage = sentMessages.stream()
			.filter(InterruptMessage.class::isInstance)
			.map(InterruptMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(interruptMessage.getRequestId()).isEqualTo(invocationMessage.getRequestId());
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
		assertThat(interruptMessage.getWebSocketSessionId()).isEqualTo("callee-ws");
	}

	@Test
	public void cancelKillIsRejectedWhenCallerDidNotAnnounceCallCanceling() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(false, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILL);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(false, false));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getType()).isEqualTo(cancelMessage.getCode());
		assertThat(errorMessage.getRequestId()).isEqualTo(10L);
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void cancelSkipCancelsCallerWithoutInterrupt() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_SKIP);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(cancelMessage);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		ArgumentCaptor<WampMessage> postCancelCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(postCancelCaptor.capture());
		List<WampMessage> sentMessages = postCancelCaptor.getAllValues();
		assertThat(sentMessages).hasSize(1);
		assertThat(sentMessages.stream().filter(InterruptMessage.class::isInstance).toList()).isEmpty();

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.filter(message -> message.getRequestId() == 10L)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.CANCELED.getExternalValue());
	}

	@Test
	public void cancelWithoutModeDefaultsToSkipWithoutCallerFeature() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(cancelMessage);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		ArgumentCaptor<WampMessage> cancelCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(cancelCaptor.capture());
		List<WampMessage> sentMessages = cancelCaptor.getAllValues();
		assertThat(sentMessages).hasSize(1);
		assertThat(sentMessages.stream().filter(InterruptMessage.class::isInstance).toList()).isEmpty();

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.CANCELED.getExternalValue());
	}

	@Test
	public void cancelKillIsRejectedWhenDealerFeatureIsDisabled() {
		Features features = new Features();
		features.disable(Feature.DEALER_CALL_CANCELING);
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(features);
		RpcMessageHandler disabledFeatureHandler = new RpcMessageHandler(this.clientInboundChannel,
				this.clientOutboundChannel, procedureRegistry, this.handlerMethodService, features);
		disabledFeatureHandler.setApplicationContext(this.applicationContext);
		disabledFeatureHandler.start();

		RegisterMessage registerMessage = new RegisterMessage(1L, "com.myapp.test");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 200L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "callee-ws");
		registerMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, calleeRoles(true, false));
		disabledFeatureHandler.handleMessage(registerMessage);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		disabledFeatureHandler.handleMessage(callMessage);

		captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILL);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		disabledFeatureHandler.handleMessage(cancelMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());

		disabledFeatureHandler.stop();
	}

	@Test
	public void cancelKillWaitsForInvocationCompletion() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILL);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		ArgumentCaptor<WampMessage> interruptCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(interruptCaptor.capture());
		List<WampMessage> interruptMessages = interruptCaptor.getAllValues();
		assertThat(interruptMessages).hasSize(1);

		InterruptMessage interruptMessage = interruptMessages.stream()
			.filter(InterruptMessage.class::isInstance)
			.map(InterruptMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(interruptMessage.getRequestId()).isEqualTo(invocationMessage.getRequestId());
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILL);

		Mockito.clearInvocations(this.clientOutboundChannel);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("done"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		ArgumentCaptor<WampMessage> resultCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(resultCaptor.capture());
		List<WampMessage> resultMessages = resultCaptor.getAllValues();
		assertThat(resultMessages).hasSize(1);

		ResultMessage resultMessage = resultMessages.stream()
			.filter(ResultMessage.class::isInstance)
			.map(ResultMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(resultMessage.getRequestId()).isEqualTo(10L);
		assertThat(resultMessage.getArguments()).containsExactly("done");
		assertThat(resultMessage.getWebSocketSessionId()).isEqualTo("caller-ws");
	}

	@Test
	public void cancelKillNoWaitFallsBackToSkipWhenCalleeDidNotAnnounceCallCanceling() {
		registerProcedureWithoutCallCanceling();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILLNOWAIT);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		ArgumentCaptor<WampMessage> postCancelCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(postCancelCaptor.capture());
		List<WampMessage> sentMessages = postCancelCaptor.getAllValues();
		assertThat(sentMessages).hasSize(1);
		assertThat(sentMessages.stream().filter(InterruptMessage.class::isInstance).toList()).isEmpty();

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.filter(message -> message.getRequestId() == 10L)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.CANCELED.getExternalValue());
	}

	@Test
	public void cancelRejectsInvalidModeEvenWhenCalleeDoesNotAnnounceCallCanceling() {
		registerProcedureWithoutCallCanceling();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, "bogus");
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getType()).isEqualTo(cancelMessage.getCode());
		assertThat(errorMessage.getRequestId()).isEqualTo(10L);
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void callerDisconnectCancelsPendingInvocationWithKillNoWait() {
		registerProcedure();

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		Mockito.clearInvocations(this.clientOutboundChannel);

		this.rpcMessageHandler.handleDisconnectEvent(new WampDisconnectEvent(0L, "caller-ws", null));

		ArgumentCaptor<WampMessage> interruptCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(interruptCaptor.capture());
		InterruptMessage interruptMessage = interruptCaptor.getAllValues()
			.stream()
			.filter(InterruptMessage.class::isInstance)
			.map(InterruptMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(interruptMessage.getRequestId()).isEqualTo(invocationMessage.getRequestId());
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);
		assertThat(interruptMessage.getWebSocketSessionId()).isEqualTo("callee-ws");

		Mockito.clearInvocations(this.clientOutboundChannel);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		Mockito.verifyNoInteractions(this.clientOutboundChannel);
	}

	@Test
	public void callTimeoutIsRejectedWhenCallerDidNotAnnounceFeature() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, false, 100L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getType()).isEqualTo(callMessage.getCode());
		assertThat(errorMessage.getRequestId()).isEqualTo(10L);
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void callTimeoutCancelsPendingInvocationAndErrorsCaller() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, false, 25L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> timeoutCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.timeout(1500).times(3)).send(timeoutCaptor.capture());
		List<WampMessage> sentMessages = timeoutCaptor.getAllValues();

		InvocationMessage invocationMessage = sentMessages.stream()
			.filter(InvocationMessage.class::isInstance)
			.map(InvocationMessage.class::cast)
			.findFirst()
			.orElseThrow();

		InterruptMessage interruptMessage = sentMessages.stream()
			.filter(InterruptMessage.class::isInstance)
			.map(InterruptMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(interruptMessage.getRequestId()).isEqualTo(invocationMessage.getRequestId());
		assertThat(interruptMessage.getMode()).isEqualTo(CancelMessage.MODE_KILLNOWAIT);

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getRequestId()).isEqualTo(10L);
		assertThat(errorMessage.getError()).isEqualTo(WampError.TIMEOUT.getExternalValue());

		Mockito.clearInvocations(this.clientOutboundChannel);

		YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(), List.of("late"), null);
		this.rpcMessageHandler.handleMessage(yieldMessage);

		Mockito.verifyNoInteractions(this.clientOutboundChannel);
	}

	@Test
	public void cancelKillTimesOutAsCanceledWithoutSendingSecondInterrupt() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, false, 25L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CancelMessage cancelMessage = new CancelMessage(10L, CancelMessage.MODE_KILL);
		cancelMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		cancelMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, true));
		this.rpcMessageHandler.handleMessage(cancelMessage);

		ArgumentCaptor<WampMessage> cancelCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.timeout(1500).times(2)).send(cancelCaptor.capture());
		List<WampMessage> sentMessages = cancelCaptor.getAllValues();

		List<InterruptMessage> interruptMessages = sentMessages.stream()
			.filter(InterruptMessage.class::isInstance)
			.map(InterruptMessage.class::cast)
			.toList();
		assertThat(interruptMessages).hasSize(1);
		assertThat(interruptMessages.get(0).getRequestId()).isEqualTo(invocationMessage.getRequestId());
		assertThat(interruptMessages.get(0).getMode()).isEqualTo(CancelMessage.MODE_KILL);

		ErrorMessage errorMessage = sentMessages.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getRequestId()).isEqualTo(10L);
		assertThat(errorMessage.getError()).isEqualTo(WampError.CANCELED.getExternalValue());
	}

	@Test
	public void callTimeoutIsForwardedToTimeoutCapableCallee() {
		registerProcedure(true, true);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, false, 5000L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> invocationCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(invocationCaptor.capture());
		InvocationMessage invocationMessage = invocationCaptor.getAllValues()
			.stream()
			.filter(InvocationMessage.class::isInstance)
			.map(InvocationMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(invocationMessage.getTimeout()).isEqualTo(5000L);
	}

	@Test
	public void callTimeoutRejectsNonPositiveValues() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, false, 0L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void receiveProgressIsRejectedWhenCallerDidNotAnnounceFeature() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, true);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());
	}

	@Test
	public void receiveProgressIsRejectedWhenCalleeDoesNotSupportFeature() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, true);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.FEATURE_NOT_SUPPORTED.getExternalValue());
	}

	@Test
	public void progressiveYieldKeepsInvocationOpenUntilFinalResult() {
		registerProcedure("callee-ws", "com.myapp.test", MatchPolicy.EXACT, InvocationPolicy.SINGLE, true, false, false,
				true);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, false, true);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, callerRoles(true, false, true));
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(2);
		assertThat(invocationMessage.isReceiveProgress()).isTrue();
		Mockito.clearInvocations(this.clientOutboundChannel);

		YieldMessage partialYield = new YieldMessage(invocationMessage.getRequestId(), true, List.of("part"), null);
		this.rpcMessageHandler.handleMessage(partialYield);

		YieldMessage finalYield = new YieldMessage(invocationMessage.getRequestId(), List.of("done"), null);
		this.rpcMessageHandler.handleMessage(finalYield);

		ArgumentCaptor<WampMessage> resultCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(2)).send(resultCaptor.capture());
		List<ResultMessage> resultMessages = resultCaptor.getAllValues()
			.stream()
			.filter(ResultMessage.class::isInstance)
			.map(ResultMessage.class::cast)
			.toList();
		assertThat(resultMessages).hasSize(2);
		assertThat(resultMessages.get(0).isProgress()).isTrue();
		assertThat(resultMessages.get(0).getArguments()).containsExactly("part");
		assertThat(resultMessages.get(1).isProgress()).isFalse();
		assertThat(resultMessages.get(1).getArguments()).containsExactly("done");
	}

	@Test
	public void discloseCallerAddsCallerAuthDetailsToInvocation() {
		registerProcedure();
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.test", List.of("work"), null, true);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 100L);
		callMessage.setHeader(WampMessageHeader.PRINCIPAL, new TestPrincipal("alice", "ROLE_ADMIN"));
		this.rpcMessageHandler.handleMessage(callMessage);

		ArgumentCaptor<WampMessage> invocationCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(invocationCaptor.capture());
		InvocationMessage invocationMessage = invocationCaptor.getAllValues()
			.stream()
			.filter(InvocationMessage.class::isInstance)
			.map(InvocationMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(invocationMessage.getCaller()).isEqualTo(100L);
		assertThat(invocationMessage.getCallerAuthId()).isEqualTo("alice");
		assertThat(invocationMessage.getCallerAuthRole()).isEqualTo("ADMIN");
	}

	@Test
	public void patternBasedRegistrationRequiresDealerFeature() {
		Features features = new Features();
		features.disable(Feature.DEALER_PATTERN_BASED_REGISTRATION);
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(features);
		RpcMessageHandler disabledFeatureHandler = new RpcMessageHandler(this.clientInboundChannel,
				this.clientOutboundChannel, procedureRegistry, this.handlerMethodService, features);
		disabledFeatureHandler.setApplicationContext(this.applicationContext);
		disabledFeatureHandler.start();

		RegisterMessage registerMessage = new RegisterMessage(1L, "com.myapp.orders", false, MatchPolicy.PREFIX);
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 200L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "callee-ws");
		disabledFeatureHandler.handleMessage(registerMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());

		disabledFeatureHandler.stop();
	}

	@Test
	public void sharedRegistrationRequiresDealerFeature() {
		Features features = new Features();
		features.disable(Feature.DEALER_SHARED_REGISTRATION);
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(features);
		RpcMessageHandler disabledFeatureHandler = new RpcMessageHandler(this.clientInboundChannel,
				this.clientOutboundChannel, procedureRegistry, this.handlerMethodService, features);
		disabledFeatureHandler.setApplicationContext(this.applicationContext);
		disabledFeatureHandler.start();

		RegisterMessage registerMessage = new RegisterMessage(1L, "com.myapp.worker", false, MatchPolicy.EXACT,
				InvocationPolicy.ROUNDROBIN);
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 200L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "callee-ws");
		disabledFeatureHandler.handleMessage(registerMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.OPTION_NOT_ALLOWED.getExternalValue());

		disabledFeatureHandler.stop();
	}

	@Test
	public void sharedRegistrationRejectsMismatchedInvocationPolicy() {
		registerProcedure("callee-1", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.ROUNDROBIN);
		Mockito.clearInvocations(this.clientOutboundChannel);

		RegisterMessage registerMessage = new RegisterMessage(2L, "com.myapp.worker", false, MatchPolicy.EXACT,
				InvocationPolicy.FIRST);
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 201L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "callee-2");
		registerMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, calleeRoles(true, false));
		this.rpcMessageHandler.handleMessage(registerMessage);

		ArgumentCaptor<WampMessage> errorCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(errorCaptor.capture());
		ErrorMessage errorMessage = errorCaptor.getAllValues()
			.stream()
			.filter(ErrorMessage.class::isInstance)
			.map(ErrorMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(errorMessage.getError()).isEqualTo(WampError.PROCEDURE_ALREADY_EXISTS.getExternalValue());
	}

	@Test
	public void sharedRegistrationRoundRobinDispatchesAcrossCallees() {
		registerProcedure("callee-1", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.ROUNDROBIN);
		registerProcedure("callee-2", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.ROUNDROBIN);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage firstCall = new CallMessage(10L, "com.myapp.worker");
		firstCall.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(firstCall);

		CallMessage secondCall = new CallMessage(11L, "com.myapp.worker");
		secondCall.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(secondCall);

		ArgumentCaptor<WampMessage> invocationCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(2)).send(invocationCaptor.capture());
		List<InvocationMessage> invocations = invocationCaptor.getAllValues()
			.stream()
			.filter(InvocationMessage.class::isInstance)
			.map(InvocationMessage.class::cast)
			.toList();
		assertThat(invocations).hasSize(2);
		assertThat(invocations.get(0).getWebSocketSessionId()).isEqualTo("callee-1");
		assertThat(invocations.get(1).getWebSocketSessionId()).isEqualTo("callee-2");
	}

	@Test
	public void sharedRegistrationFirstUsesFirstRegisteredCallee() {
		registerProcedure("callee-1", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.FIRST);
		registerProcedure("callee-2", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.FIRST);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.worker");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		assertThat(invocationMessage.getWebSocketSessionId()).isEqualTo("callee-1");
	}

	@Test
	public void sharedRegistrationLastUsesLastRegisteredCallee() {
		registerProcedure("callee-1", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.LAST);
		registerProcedure("callee-2", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.LAST);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.worker");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		assertThat(invocationMessage.getWebSocketSessionId()).isEqualTo("callee-2");
	}

	@Test
	public void exactRegistrationWinsOverPatternRegistration() {
		registerProcedure("callee-prefix", "com.myapp.orders", MatchPolicy.PREFIX);
		registerProcedure("callee-exact", "com.myapp.orders.create", MatchPolicy.EXACT);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.orders.create");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		assertThat(invocationMessage.getWebSocketSessionId()).isEqualTo("callee-exact");
		assertThat(invocationMessage.getProcedure()).isNull();
	}

	@Test
	public void patternRegistrationUsesLongestPrefixAndForwardsProcedure() {
		registerProcedure("callee-prefix-short", "com.myapp", MatchPolicy.PREFIX);
		registerProcedure("callee-prefix-long", "com.myapp.orders", MatchPolicy.PREFIX);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.orders.create");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		assertThat(invocationMessage.getWebSocketSessionId()).isEqualTo("callee-prefix-long");
		assertThat(invocationMessage.getProcedure()).isEqualTo("com.myapp.orders.create");
	}

	@Test
	public void patternRegistrationUsesBestWildcardMatch() {
		registerProcedure("callee-wildcard-short", "com.myapp..create", MatchPolicy.WILDCARD);
		registerProcedure("callee-wildcard-best", "com.myapp.orders..create", MatchPolicy.WILDCARD);
		Mockito.clearInvocations(this.clientOutboundChannel);

		CallMessage callMessage = new CallMessage(10L, "com.myapp.orders.us.create");
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "caller-ws");
		this.rpcMessageHandler.handleMessage(callMessage);

		InvocationMessage invocationMessage = captureSingleInvocation(1);
		assertThat(invocationMessage.getWebSocketSessionId()).isEqualTo("callee-wildcard-best");
		assertThat(invocationMessage.getProcedure()).isEqualTo("com.myapp.orders.us.create");
	}

	@Test
	public void registrationRevocationSendsExtendedUnregisteredOnlyToSupportingCallees() {
		registerProcedure("callee-support", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.ROUNDROBIN, true,
				false, true);
		long registrationId = captureSingleRegisteredMessage(1).getRegistrationId();
		Mockito.clearInvocations(this.clientOutboundChannel);

		registerProcedure("callee-basic", "com.myapp.worker", MatchPolicy.EXACT, InvocationPolicy.ROUNDROBIN, true,
				false, false);
		RegisteredMessage secondRegistered = captureSingleRegisteredMessage(1);
		assertThat(secondRegistered.getRegistrationId()).isEqualTo(registrationId);
		Mockito.clearInvocations(this.clientOutboundChannel);

		int revoked = this.rpcMessageHandler.revokeRegistration(registrationId, "moving endpoint to other callee");

		assertThat(revoked).isEqualTo(2);
		ArgumentCaptor<WampMessage> messageCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1)).send(messageCaptor.capture());
		UnregisteredMessage unregisteredMessage = messageCaptor.getAllValues()
			.stream()
			.filter(UnregisteredMessage.class::isInstance)
			.map(UnregisteredMessage.class::cast)
			.findFirst()
			.orElseThrow();
		assertThat(unregisteredMessage.getRequestId()).isZero();
		assertThat(unregisteredMessage.getRegistrationId()).isEqualTo(registrationId);
		assertThat(unregisteredMessage.getReason()).isEqualTo("moving endpoint to other callee");
		assertThat(unregisteredMessage.getWebSocketSessionId()).isEqualTo("callee-support");
	}

	private void registerProcedure() {
		registerProcedure(true, false);
	}

	private void registerProcedureWithoutCallCanceling() {
		registerProcedure(false, false);
	}

	private void registerProcedure(boolean callCancelingSupported, boolean callTimeoutSupported) {
		registerProcedure("callee-ws", "com.myapp.test", MatchPolicy.EXACT, callCancelingSupported,
				callTimeoutSupported);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy) {
		registerProcedure(webSocketSessionId, procedure, matchPolicy, InvocationPolicy.SINGLE, true, false);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy,
			InvocationPolicy invocationPolicy) {
		registerProcedure(webSocketSessionId, procedure, matchPolicy, invocationPolicy, true, false, false);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy,
			boolean callCancelingSupported, boolean callTimeoutSupported) {
		registerProcedure(webSocketSessionId, procedure, matchPolicy, InvocationPolicy.SINGLE, callCancelingSupported,
				callTimeoutSupported, false);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy,
			InvocationPolicy invocationPolicy, boolean callCancelingSupported, boolean callTimeoutSupported) {
		registerProcedure(webSocketSessionId, procedure, matchPolicy, invocationPolicy, callCancelingSupported,
				callTimeoutSupported, false);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy,
			InvocationPolicy invocationPolicy, boolean callCancelingSupported, boolean callTimeoutSupported,
			boolean registrationRevocationSupported) {
		registerProcedure(webSocketSessionId, procedure, matchPolicy, invocationPolicy, callCancelingSupported,
				callTimeoutSupported, registrationRevocationSupported, false);
	}

	private void registerProcedure(String webSocketSessionId, String procedure, MatchPolicy matchPolicy,
			InvocationPolicy invocationPolicy, boolean callCancelingSupported, boolean callTimeoutSupported,
			boolean registrationRevocationSupported, boolean progressiveCallResultsSupported) {
		RegisterMessage registerMessage = new RegisterMessage(1L, procedure, false, matchPolicy, invocationPolicy);
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, Math.abs((long) webSocketSessionId.hashCode()));
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		registerMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES, calleeRoles(callCancelingSupported,
				callTimeoutSupported, registrationRevocationSupported, progressiveCallResultsSupported));
		this.rpcMessageHandler.handleMessage(registerMessage);
	}

	private static List<WampRole> calleeRoles(boolean callCancelingSupported, boolean callTimeoutSupported) {
		return calleeRoles(callCancelingSupported, callTimeoutSupported, false, false);
	}

	private static List<WampRole> calleeRoles(boolean callCancelingSupported, boolean callTimeoutSupported,
			boolean registrationRevocationSupported, boolean progressiveCallResultsSupported) {
		WampRole callee = new WampRole("callee");
		if (callCancelingSupported) {
			callee.addFeature(Feature.DEALER_CALL_CANCELING.getExternalValue());
		}
		if (callTimeoutSupported) {
			callee.addFeature(Feature.DEALER_CALL_TIMEOUT.getExternalValue());
		}
		if (registrationRevocationSupported) {
			callee.addFeature(Feature.DEALER_REGISTRATION_REVOCATION.getExternalValue());
		}
		if (progressiveCallResultsSupported) {
			callee.addFeature(Feature.DEALER_PROGRESSIVE_CALL_RESULTS.getExternalValue());
		}
		return List.of(callee);
	}

	private static List<WampRole> callerRoles(boolean callCancelingSupported, boolean callTimeoutSupported) {
		return callerRoles(callCancelingSupported, callTimeoutSupported, false);
	}

	private static List<WampRole> callerRoles(boolean callCancelingSupported, boolean callTimeoutSupported,
			boolean progressiveCallResultsSupported) {
		WampRole caller = new WampRole("caller");
		if (callCancelingSupported) {
			caller.addFeature(Feature.DEALER_CALL_CANCELING.getExternalValue());
		}
		if (callTimeoutSupported) {
			caller.addFeature(Feature.DEALER_CALL_TIMEOUT.getExternalValue());
		}
		if (progressiveCallResultsSupported) {
			caller.addFeature(Feature.DEALER_PROGRESSIVE_CALL_RESULTS.getExternalValue());
		}
		return List.of(caller);
	}

	private InvocationMessage captureSingleInvocation(int expectedSendCount) {
		ArgumentCaptor<WampMessage> initialCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(expectedSendCount)).send(initialCaptor.capture());
		return initialCaptor.getAllValues()
			.stream()
			.filter(InvocationMessage.class::isInstance)
			.map(InvocationMessage.class::cast)
			.findFirst()
			.orElseThrow();
	}

	private RegisteredMessage captureSingleRegisteredMessage(int expectedSendCount) {
		ArgumentCaptor<WampMessage> initialCaptor = ArgumentCaptor.forClass(WampMessage.class);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(expectedSendCount)).send(initialCaptor.capture());
		return initialCaptor.getAllValues()
			.stream()
			.filter(RegisteredMessage.class::isInstance)
			.map(RegisteredMessage.class::cast)
			.findFirst()
			.orElseThrow();
	}

	private static final class TestPrincipal implements java.security.Principal {

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