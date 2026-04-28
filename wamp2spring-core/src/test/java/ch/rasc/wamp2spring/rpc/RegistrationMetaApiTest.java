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
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampProcedureRegisteredEvent;
import ch.rasc.wamp2spring.event.WampProcedureUnregisteredEvent;
import ch.rasc.wamp2spring.event.WampRegistrationCreatedEvent;
import ch.rasc.wamp2spring.event.WampRegistrationDeletedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class RegistrationMetaApiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void exposesRegistrationMetaProcedures() throws WampException {
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(new Features());
		long registrationId = procedureRegistry
			.register(registerMessage(1L, 101L, "callee-1", "com.myapp.worker", InvocationPolicy.ROUNDROBIN))
			.getRegistrationId();
		procedureRegistry
			.register(registerMessage(2L, 202L, "callee-2", "com.myapp.worker", InvocationPolicy.ROUNDROBIN));

		RegistrationMetaApi api = new RegistrationMetaApi(procedureRegistry,
				new WampPublisher(Mockito.mock(MessageChannel.class)));

		WampResult listResult = api.list();
		WampResult lookupResult = api.lookup(new CallMessage(10L, RegistrationMetaApi.LOOKUP,
				List.of("com.myapp.worker", Map.of("match", "exact"))));
		WampResult matchResult = api
			.match(new CallMessage(11L, RegistrationMetaApi.MATCH, List.of("com.myapp.worker")));
		WampResult getResult = api.get(new CallMessage(12L, RegistrationMetaApi.GET, List.of(registrationId)));
		WampResult listCalleesResult = api
			.listCallees(new CallMessage(13L, RegistrationMetaApi.LIST_CALLEES, List.of(registrationId)));
		WampResult countCalleesResult = api
			.countCallees(new CallMessage(14L, RegistrationMetaApi.COUNT_CALLEES, List.of(registrationId)));

		Map<String, List<Long>> listedRegistrations = (Map<String, List<Long>>) Objects
			.requireNonNull(listResult.getResults())
			.get(0);
		assertThat(listedRegistrations.get("exact")).containsExactly(registrationId);
		assertThat(Objects.requireNonNull(lookupResult.getResults())).containsExactly(registrationId);
		assertThat(Objects.requireNonNull(matchResult.getResults())).containsExactly(registrationId);

		Map<String, Object> detail = (Map<String, Object>) Objects.requireNonNull(getResult.getResults()).get(0);
		assertThat(detail.get("id")).isEqualTo(registrationId);
		assertThat(detail.get("uri")).isEqualTo("com.myapp.worker");
		assertThat(detail.get("match")).isEqualTo(MatchPolicy.EXACT.getExternalValue());
		assertThat(detail.get("invoke")).isEqualTo(InvocationPolicy.ROUNDROBIN.getExternalValue());
		assertThat(detail.get("created")).isInstanceOf(String.class);
		assertThat((List<Long>) Objects.requireNonNull(listCalleesResult.getResults()).get(0))
			.containsExactlyInAnyOrder(101L, 202L);
		assertThat(Objects.requireNonNull(countCalleesResult.getResults())).containsExactly(2);

		assertThatThrownBy(() -> api.get(new CallMessage(15L, RegistrationMetaApi.GET, List.of(999L))))
			.isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.NO_SUCH_REGISTRATION.getExternalValue());

		assertThatThrownBy(
				() -> api.lookup(new CallMessage(16L, RegistrationMetaApi.LOOKUP, List.of("wamp.custom", Map.of()))))
			.isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.INVALID_URI.getExternalValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void publishesRegistrationLifecycleTopics() {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(brokerChannel.send(ArgumentMatchers.any(PublishMessage.class))).thenReturn(true);

		ProcedureRegistry procedureRegistry = new ProcedureRegistry(new Features());
		RegisterMessage registerMessage = registerMessage(1L, 101L, "callee-1", "com.myapp.worker",
				InvocationPolicy.ROUNDROBIN);
		long registrationId = procedureRegistry.register(registerMessage).getRegistrationId();

		RegistrationMetaApi api = new RegistrationMetaApi(procedureRegistry, new WampPublisher(brokerChannel));
		api.onRegistrationCreated(new WampRegistrationCreatedEvent(registerMessage, registrationId));
		api.onProcedureRegistered(new WampProcedureRegisteredEvent(registerMessage, registrationId));

		UnregisterMessage unregisterMessage = unregisterMessage(2L, registrationId, "callee-1");
		unregisterMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		api.onProcedureUnregistered(
				new WampProcedureUnregisteredEvent(unregisterMessage, "com.myapp.worker", registrationId));
		api.onRegistrationDeleted(
				new WampRegistrationDeletedEvent(unregisterMessage, "com.myapp.worker", registrationId));

		ArgumentCaptor<PublishMessage> publishCaptor = ArgumentCaptor.forClass(PublishMessage.class);
		Mockito.verify(brokerChannel, Mockito.times(4)).send(publishCaptor.capture());
		List<PublishMessage> publishedMessages = publishCaptor.getAllValues();
		assertThat(publishedMessages.get(0).getTopic()).isEqualTo(RegistrationMetaApi.ON_CREATE);
		assertThat(publishedMessages.get(1).getTopic()).isEqualTo(RegistrationMetaApi.ON_REGISTER);
		assertThat(publishedMessages.get(2).getTopic()).isEqualTo(RegistrationMetaApi.ON_UNREGISTER);
		assertThat(publishedMessages.get(3).getTopic()).isEqualTo(RegistrationMetaApi.ON_DELETE);

		List<Object> createArguments = Objects.requireNonNull(publishedMessages.get(0).getArguments());
		assertThat(createArguments.get(0)).isEqualTo(101L);
		assertThat(((Map<String, Object>) createArguments.get(1)).get("id")).isEqualTo(registrationId);
		assertThat(publishedMessages.get(1).getArguments()).containsExactly(101L, registrationId);
		assertThat(publishedMessages.get(2).getArguments()).containsExactly(101L, registrationId);
		assertThat(publishedMessages.get(3).getArguments()).containsExactly(101L, registrationId);
	}

	private static RegisterMessage registerMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String procedure, InvocationPolicy invocationPolicy) {
		RegisterMessage message = new RegisterMessage(requestId, procedure, false, MatchPolicy.EXACT, invocationPolicy);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

	private static UnregisterMessage unregisterMessage(long requestId, long registrationId, String webSocketSessionId) {
		UnregisterMessage message = new UnregisterMessage(requestId, registrationId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

}