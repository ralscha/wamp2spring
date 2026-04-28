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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.rpc.ProcedureRegistry.CallProc;

@SuppressWarnings("unchecked")
public class ProcedureRegistryTest {

	private ProcedureRegistry procedureRegistry;

	@BeforeEach
	public void setup() {
		this.procedureRegistry = new ProcedureRegistry(new Features());
	}

	@Test
	public void testRegister() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		assertThat(registerResult.isCreated()).isTrue();
		long regId = registerResult.getRegistrationId();

		registerMessage = new RegisterMessage(2L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		RegisterResult failedRegister = this.procedureRegistry.register(registerMessage);
		assertThat(failedRegister.isSuccess()).isFalse();
		assertThat(failedRegister.isCreated()).isFalse();
		assertThat(failedRegister.getRegistrationId()).isEqualTo(-1L);

		assertThat(this.procedureRegistry.listRegistrations().get(MatchPolicy.EXACT)).containsExactly(regId);
		assertThat(this.procedureRegistry.lookupRegistration("service.add", MatchPolicy.EXACT)).isEqualTo(regId);

		ProcedureDetail detail = this.procedureRegistry.getRegistration(regId);
		assertThat(detail).isNotNull();
		ProcedureDetail requiredDetail = Objects.requireNonNull(detail);
		assertThat(requiredDetail.getProcedure()).isEqualTo("service.add");
		assertThat(requiredDetail.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(this.procedureRegistry.listCallees(regId)).containsExactly(123L);
		assertThat(this.procedureRegistry.countCallees(regId)).isEqualTo(1);
	}

	@Test
	public void testUnregister() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		long regId = registerResult.getRegistrationId();
		assertThat(this.procedureRegistry.getRegistration(regId)).isNotNull();
		assertThat(this.procedureRegistry.countCallees(regId)).isEqualTo(1);

		UnregisterMessage unregisterMessage = new UnregisterMessage(2L, regId);
		unregisterMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		UnregisterResult result = this.procedureRegistry.unregister(unregisterMessage);
		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getInvocationErrors()).isEmpty();
		assertThat(result.isDeleted()).isTrue();
		assertThat(this.procedureRegistry.getRegistration(regId)).isNull();

		unregisterMessage = new UnregisterMessage(3L, regId);
		unregisterMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		result = this.procedureRegistry.unregister(unregisterMessage);
		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getInvocationErrors()).isNull();
	}

	@Test
	public void testUnregisterWebSocketSession() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		long regId = registerResult.getRegistrationId();

		this.procedureRegistry.unregisterWebSocketSession("two");
		assertThat(this.procedureRegistry.getRegistration(regId)).isNotNull();
		assertThat(this.procedureRegistry.countCallees(regId)).isEqualTo(1);

		this.procedureRegistry.unregisterWebSocketSession("one");
		assertThat(this.procedureRegistry.getRegistration(regId)).isNull();
		assertThat(this.procedureRegistry.listRegistrations().get(MatchPolicy.EXACT)).isEmpty();
	}

	@Test
	public void testCreateInvocationMessage() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		long regId = registerResult.getRegistrationId();

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		Map<Long, CallProc> pendingInvocations = Objects.requireNonNull(
				(Map<Long, CallProc>) ReflectionTestUtils.getField(this.procedureRegistry, "pendingInvocations"));
		assertThat(pendingInvocations).containsOnlyKeys(im.getRequestId());
		CallProc cp = Objects.requireNonNull(pendingInvocations.get(im.getRequestId()));
		assertThat(cp.callMessage).isEqualTo(callMessage);
	}

	@Test
	public void testCreateInvocationMessageError() {
		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(ErrorMessage.class);
		ErrorMessage er = (ErrorMessage) msg;
		assertThat(er.getRequestId()).isEqualTo(callMessage.getRequestId());
		assertThat(er.getWebSocketSessionId()).isEqualTo("two");

		Map<Long, CallMessage> pendingInvocations = (Map<Long, CallMessage>) ReflectionTestUtils
			.getField(this.procedureRegistry, "pendingInvocations");
		assertThat(pendingInvocations).isEmpty();
	}

	@Test
	public void testRemoveInvocationCall() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		long regId = registerResult.getRegistrationId();

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		YieldMessage yieldMessage = new YieldMessage(im.getRequestId(), null, null);
		CallMessage callMessage2 = this.procedureRegistry.removeInvocationCall(yieldMessage);
		assertThat(callMessage).isEqualTo(callMessage2);

		Map<Long, CallMessage> pendingInvocations = (Map<Long, CallMessage>) ReflectionTestUtils
			.getField(this.procedureRegistry, "pendingInvocations");
		assertThat(pendingInvocations).isEmpty();
	}

	@Test
	public void testRemoveInvocationCallError() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		RegisterResult registerResult = this.procedureRegistry.register(registerMessage);
		assertThat(registerResult.isSuccess()).isTrue();
		long regId = registerResult.getRegistrationId();

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		ErrorMessage errorMessage = new ErrorMessage(im.getCode(), im.getRequestId(), "error", null, null);
		CallMessage callMessage2 = this.procedureRegistry.removeInvocationCall(errorMessage);
		assertThat(callMessage).isEqualTo(callMessage2);

		Map<Long, CallMessage> pendingInvocations = (Map<Long, CallMessage>) ReflectionTestUtils
			.getField(this.procedureRegistry, "pendingInvocations");
		assertThat(pendingInvocations).isEmpty();

		errorMessage = new ErrorMessage(im.getCode(), 111L, "error", null, null);
		callMessage2 = this.procedureRegistry.removeInvocationCall(errorMessage);
		assertThat(callMessage2).isNull();
	}

}
