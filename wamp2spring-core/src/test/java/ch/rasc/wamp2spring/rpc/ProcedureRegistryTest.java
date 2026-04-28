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

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class ProcedureRegistryTest {

	@Test
	public void sharedRegistrationUsesOneRegistrationIdAndTracksCallees() {
		ProcedureRegistry registry = new ProcedureRegistry(new Features());

		RegisterResult first = registry
			.register(registerMessage(1L, 101L, "callee-1", "com.myapp.worker", InvocationPolicy.ROUNDROBIN));
		RegisterResult second = registry
			.register(registerMessage(2L, 202L, "callee-2", "com.myapp.worker", InvocationPolicy.ROUNDROBIN));

		assertThat(first.isSuccess()).isTrue();
		assertThat(first.isCreated()).isTrue();
		assertThat(second.isSuccess()).isTrue();
		assertThat(second.isCreated()).isFalse();
		assertThat(second.getRegistrationId()).isEqualTo(first.getRegistrationId());
		assertThat(registry.listRegistrations().get(MatchPolicy.EXACT)).containsExactly(first.getRegistrationId());
		assertThat(registry.lookupRegistration("com.myapp.worker", MatchPolicy.EXACT))
			.isEqualTo(first.getRegistrationId());
		assertThat(registry.matchRegistration("com.myapp.worker")).isEqualTo(first.getRegistrationId());

		ProcedureDetail detail = registry.getRegistration(first.getRegistrationId());
		assertThat(detail).isNotNull();
		ProcedureDetail requiredDetail = Objects.requireNonNull(detail);
		assertThat(requiredDetail.getProcedure()).isEqualTo("com.myapp.worker");
		assertThat(requiredDetail.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);
		assertThat(requiredDetail.getInvocationPolicy()).isEqualTo(InvocationPolicy.ROUNDROBIN);
		assertThat(registry.listCallees(first.getRegistrationId())).containsExactlyInAnyOrder(101L, 202L);
		assertThat(registry.countCallees(first.getRegistrationId())).isEqualTo(2);
	}

	@Test
	public void sharedRegistrationDeletesSlotWhenLastCalleeLeaves() {
		ProcedureRegistry registry = new ProcedureRegistry(new Features());
		RegisterResult first = registry
			.register(registerMessage(1L, 101L, "callee-1", "com.myapp.worker", InvocationPolicy.ROUNDROBIN));
		registry.register(registerMessage(2L, 202L, "callee-2", "com.myapp.worker", InvocationPolicy.ROUNDROBIN));

		UnregisterResult unregisterFirst = registry
			.unregister(unregisterMessage(3L, first.getRegistrationId(), "callee-1"));

		assertThat(unregisterFirst.isSuccess()).isTrue();
		assertThat(unregisterFirst.isDeleted()).isFalse();
		assertThat(registry.countCallees(first.getRegistrationId())).isEqualTo(1);

		UnregisterResult unregisterSecond = registry
			.unregister(unregisterMessage(4L, first.getRegistrationId(), "callee-2"));

		assertThat(unregisterSecond.isSuccess()).isTrue();
		assertThat(unregisterSecond.isDeleted()).isTrue();
		assertThat(registry.getRegistration(first.getRegistrationId())).isNull();
	}

	@Test
	public void registrationsAreIsolatedByRealm() {
		ProcedureRegistry registry = new ProcedureRegistry(new Features());

		RegisterResult firstRealm = registry
			.register(registerMessage(1L, 101L, "callee-1", "realm-one", "com.myapp.worker", InvocationPolicy.SINGLE));
		RegisterResult secondRealm = registry
			.register(registerMessage(2L, 202L, "callee-2", "realm-two", "com.myapp.worker", InvocationPolicy.SINGLE));

		assertThat(firstRealm.isSuccess()).isTrue();
		assertThat(secondRealm.isSuccess()).isTrue();
		assertThat(secondRealm.getRegistrationId()).isNotEqualTo(firstRealm.getRegistrationId());
		assertThat(registry.lookupRegistration("realm-one", "com.myapp.worker", MatchPolicy.EXACT))
			.isEqualTo(firstRealm.getRegistrationId());
		assertThat(registry.lookupRegistration("realm-two", "com.myapp.worker", MatchPolicy.EXACT))
			.isEqualTo(secondRealm.getRegistrationId());
		assertThat(registry.matchRegistration("realm-one", "com.myapp.worker"))
			.isEqualTo(firstRealm.getRegistrationId());
		assertThat(registry.listRegistrations("realm-two").get(MatchPolicy.EXACT))
			.containsExactly(secondRealm.getRegistrationId());
	}

	private static RegisterMessage registerMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String procedure, InvocationPolicy invocationPolicy) {
		return registerMessage(requestId, wampSessionId, webSocketSessionId, null, procedure, invocationPolicy);
	}

	private static RegisterMessage registerMessage(long requestId, long wampSessionId, String webSocketSessionId,
			@Nullable String realm, String procedure, InvocationPolicy invocationPolicy) {
		RegisterMessage message = new RegisterMessage(requestId, procedure, false, MatchPolicy.EXACT, invocationPolicy);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		message.setHeader(WampMessageHeader.WAMP_REALM, realm);
		return message;
	}

	private static UnregisterMessage unregisterMessage(long requestId, long registrationId, String webSocketSessionId) {
		UnregisterMessage message = new UnregisterMessage(requestId, registrationId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

}