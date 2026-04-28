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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.messaging.MessageChannel;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;

public class SessionTestamentMetaApiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void publishesDetachedThenDestroyedTestamentsOnSessionLeave() throws WampException {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(brokerChannel.send(ArgumentMatchers.any(PublishMessage.class))).thenReturn(true);
		SessionTestamentMetaApi api = new SessionTestamentMetaApi(new WampPublisher(brokerChannel));

		api.addTestament(addTestamentCall(1L, "com.myapp.session.detached", List.of("bye"), Map.of("kind", "detached"),
				Map.of("scope", "detached", "publish_options", Map.of("retain", true, "exclude_me", false))));
		api.addTestament(addTestamentCall(2L, "com.myapp.session.destroyed", List.of("later"),
				Map.of("kind", "destroyed"), Map.of("publish_options", Map.of("acknowledge", true))));

		api.onSessionLeft(new WampDisconnectEvent(101L, "ws-1", null));

		ArgumentCaptor<PublishMessage> publishCaptor = ArgumentCaptor.forClass(PublishMessage.class);
		Mockito.verify(brokerChannel, Mockito.times(2)).send(publishCaptor.capture());
		List<PublishMessage> publishMessages = publishCaptor.getAllValues();

		assertThat(publishMessages.get(0).getTopic()).isEqualTo("com.myapp.session.detached");
		assertThat(publishMessages.get(0).getArguments()).containsExactly("bye");
		assertThat(publishMessages.get(0).getArgumentsKw()).containsEntry("kind", "detached");
		assertThat(publishMessages.get(0).isRetain()).isTrue();
		assertThat(publishMessages.get(0).isExcludeMe()).isFalse();

		assertThat(publishMessages.get(1).getTopic()).isEqualTo("com.myapp.session.destroyed");
		assertThat(publishMessages.get(1).getArguments()).containsExactly("later");
		assertThat(publishMessages.get(1).getArgumentsKw()).containsEntry("kind", "destroyed");
		assertThat(publishMessages.get(1).isAcknowledge()).isFalse();

		Mockito.clearInvocations(brokerChannel);
		api.onSessionLeft(new WampDisconnectEvent(101L, "ws-1", null));
		Mockito.verifyNoInteractions(brokerChannel);
	}

	@Test
	public void flushTestamentsRemovesOnlyRequestedScope() throws WampException {
		MessageChannel brokerChannel = Mockito.mock(MessageChannel.class);
		Mockito.when(brokerChannel.send(ArgumentMatchers.any(PublishMessage.class))).thenReturn(true);
		SessionTestamentMetaApi api = new SessionTestamentMetaApi(new WampPublisher(brokerChannel));

		api.addTestament(addTestamentCall(1L, "com.myapp.session.detached", List.of("detached"), null,
				Map.of("scope", "detached")));
		api.addTestament(addTestamentCall(2L, "com.myapp.session.destroyed", List.of("destroyed"), null, null));

		CallMessage flush = new CallMessage(3L, SessionTestamentMetaApi.FLUSH_TESTAMENTS, null,
				Map.of("scope", "destroyed"), false);
		flush.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		api.flushTestaments(flush);

		api.onSessionLeft(new WampDisconnectEvent(101L, "ws-1", null));

		ArgumentCaptor<PublishMessage> publishCaptor = ArgumentCaptor.forClass(PublishMessage.class);
		Mockito.verify(brokerChannel, Mockito.times(1)).send(publishCaptor.capture());
		assertThat(publishCaptor.getValue().getTopic()).isEqualTo("com.myapp.session.detached");
	}

	@Test
	public void rejectsInvalidTopicAndScope() {
		SessionTestamentMetaApi api = new SessionTestamentMetaApi(
				new WampPublisher(Mockito.mock(MessageChannel.class)));

		assertThatThrownBy(() -> api.addTestament(addTestamentCall(1L, "not a uri", List.of(), null, null)))
			.isInstanceOf(WampException.class)
			.extracting(ex -> ((WampException) ex).getUri())
			.isEqualTo(WampError.INVALID_URI.getExternalValue());

		assertThatThrownBy(() -> api
			.addTestament(addTestamentCall(2L, "com.myapp.topic", List.of(), null, Map.of("scope", "other"))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static CallMessage addTestamentCall(long requestId, String topic, List<Object> args,
			@Nullable Map<String, Object> kwargs, @Nullable Map<String, Object> options) {
		List<Object> arguments = java.util.Arrays.asList(topic, args, kwargs);
		CallMessage callMessage = new CallMessage(requestId, SessionTestamentMetaApi.ADD_TESTAMENT, arguments, options,
				false);
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 101L);
		return callMessage;
	}

}