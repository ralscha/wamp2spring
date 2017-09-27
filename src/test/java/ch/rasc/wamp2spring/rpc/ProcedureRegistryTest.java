/**
 * Copyright 2017-2017 the original author or authors.
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

package ch.rasc.wamp2spring.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
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
import ch.rasc.wamp2spring.rpc.ProcedureRegistry.CallProc;

@SuppressWarnings("unchecked")
public class ProcedureRegistryTest {

	private ProcedureRegistry procedureRegistry;

	@Before
	public void setup() {
		this.procedureRegistry = new ProcedureRegistry(new Features());
	}

	@Test
	public void testRegister() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		registerMessage = new RegisterMessage(2L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		long result = this.procedureRegistry.register(registerMessage);
		assertThat(result).isEqualTo(-1);

		Map<String, Procedure> procedures = (Map<String, Procedure>) ReflectionTestUtils
				.getField(this.procedureRegistry, "procedures");
		Map<Long, String> registrations = (Map<Long, String>) ReflectionTestUtils
				.getField(this.procedureRegistry, "registrations");
		assertThat(registrations).hasSize(1);
		assertThat(registrations.get(regId)).isEqualTo("service.add");
		assertThat(procedures).hasSize(1);
		Procedure proc = procedures.get("service.add");
		assertThat(proc.getProcedure()).isEqualTo("service.add");
		assertThat(proc.getRegistrationId()).isEqualTo(regId);
		assertThat(proc.getWebSocketSessionId()).isEqualTo("one");
	}

	@Test
	public void testUnregister() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		Map<String, Procedure> procedures = (Map<String, Procedure>) ReflectionTestUtils
				.getField(this.procedureRegistry, "procedures");
		Map<Long, String> registrations = (Map<Long, String>) ReflectionTestUtils
				.getField(this.procedureRegistry, "registrations");
		assertThat(registrations).hasSize(1);
		assertThat(registrations.get(regId)).isEqualTo("service.add");
		assertThat(procedures).hasSize(1);
		Procedure proc = procedures.get("service.add");
		assertThat(proc.getProcedure()).isEqualTo("service.add");
		assertThat(proc.getRegistrationId()).isEqualTo(regId);
		assertThat(proc.getWebSocketSessionId()).isEqualTo("one");

		UnregisterMessage unregisterMessage = new UnregisterMessage(2L, regId);
		UnregisterResult result = this.procedureRegistry.unregister(unregisterMessage);
		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getInvocationErrors()).isEmpty();

		unregisterMessage = new UnregisterMessage(3L, regId);
		result = this.procedureRegistry.unregister(unregisterMessage);
		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getInvocationErrors()).isNull();
	}

	@Test
	public void testUnregisterWebSocketSession() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		Map<String, Procedure> procedures = (Map<String, Procedure>) ReflectionTestUtils
				.getField(this.procedureRegistry, "procedures");
		Map<Long, String> registrations = (Map<Long, String>) ReflectionTestUtils
				.getField(this.procedureRegistry, "registrations");

		this.procedureRegistry.unregisterWebSocketSession("two");
		assertThat(registrations).hasSize(1);
		assertThat(registrations.get(regId)).isEqualTo("service.add");
		assertThat(procedures).hasSize(1);
		Procedure proc = procedures.get("service.add");
		assertThat(proc.getProcedure()).isEqualTo("service.add");
		assertThat(proc.getRegistrationId()).isEqualTo(regId);
		assertThat(proc.getWebSocketSessionId()).isEqualTo("one");

		this.procedureRegistry.unregisterWebSocketSession("one");
		assertThat(registrations).isEmpty();
		assertThat(procedures).isEmpty();
	}

	@Test
	public void testCreateInvocationMessage() {
		RegisterMessage registerMessage = new RegisterMessage(1L, "service.add");
		registerMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 123L);
		registerMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "one");
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		Map<Long, CallProc> pendingInvocations = (Map<Long, CallProc>) ReflectionTestUtils
				.getField(this.procedureRegistry, "pendingInvocations");
		assertThat(pendingInvocations).containsOnlyKeys(im.getRequestId());
		CallProc cp = pendingInvocations.get(im.getRequestId());
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
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		YieldMessage yieldMessage = new YieldMessage(im.getRequestId(), null, null);
		CallMessage callMessage2 = this.procedureRegistry
				.removeInvocationCall(yieldMessage);
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
		long regId = this.procedureRegistry.register(registerMessage);
		assertThat(regId).isNotEqualTo(-1);

		CallMessage callMessage = new CallMessage(3L, "service.add");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 124L);
		callMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, "two");
		WampMessage msg = this.procedureRegistry.createInvocationMessage(callMessage);
		assertThat(msg).isInstanceOf(InvocationMessage.class);
		InvocationMessage im = (InvocationMessage) msg;
		assertThat(im.getRegistrationId()).isEqualTo(regId);
		assertThat(im.getWebSocketSessionId()).isEqualTo("one");

		ErrorMessage errorMessage = new ErrorMessage(im.getCode(), im.getRequestId(),
				"error", null, null);
		CallMessage callMessage2 = this.procedureRegistry
				.removeInvocationCall(errorMessage);
		assertThat(callMessage).isEqualTo(callMessage2);

		Map<Long, CallMessage> pendingInvocations = (Map<Long, CallMessage>) ReflectionTestUtils
				.getField(this.procedureRegistry, "pendingInvocations");
		assertThat(pendingInvocations).isEmpty();

		errorMessage = new ErrorMessage(im.getCode(), 111L, "error", null, null);
		callMessage2 = this.procedureRegistry.removeInvocationCall(errorMessage);
		assertThat(callMessage2).isNull();
	}

}
