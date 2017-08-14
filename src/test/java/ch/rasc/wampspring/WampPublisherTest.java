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
package ch.rasc.wampspring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.SubscribableChannel;

import ch.rasc.wampspring.message.PublishMessage;
import ch.rasc.wampspring.message.WampMessage;

public class WampPublisherTest {

	@Mock
	private SubscribableChannel clientOutboundChannel;

	private WampPublisher wampPublisher;

	@Captor
	ArgumentCaptor<PublishMessage> messageCaptor;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		Mockito.when(
				this.clientOutboundChannel.send(ArgumentMatchers.any(WampMessage.class)))
				.thenReturn(true);
		this.wampPublisher = new WampPublisher(this.clientOutboundChannel);
	}

	@Test
	public void testPublishToAll() {
		this.wampPublisher.publishToAll("topic", 1);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic", Collections.singletonList(1), null,
				null);
	}

	@Test
	public void testPublishToAllList() {
		List<String> value = Arrays.asList("a", "b", "c");
		this.wampPublisher.publishToAll("topic2", value);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());
		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic2", value, null, null);
	}

	@Test
	public void testPublishToAllMap() {
		Map<String, Integer> mapValue = new HashMap<>();
		mapValue.put("one", 1);
		mapValue.put("two", 2);
		mapValue.put("three", 3);
		this.wampPublisher.publishToAll("topic3", mapValue);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic3", mapValue, null, null);
	}

	@Test
	public void testPublishTo() {
		this.wampPublisher.publishTo(Collections.singleton(123L), "topic", 1);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic", Collections.singletonList(1),
				Collections.singleton(123L), null);
	}

	@Test
	public void testPublishToList() {
		List<String> value = Arrays.asList("a", "b", "c");
		this.wampPublisher.publishTo(Collections.singleton(123L), "topic2", value);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());
		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic2", value, Collections.singleton(123L),
				null);
	}

	@Test
	public void testPublishToMap() {
		Map<String, Integer> mapValue = new HashMap<>();
		mapValue.put("one", 1);
		mapValue.put("two", 2);
		mapValue.put("three", 3);
		this.wampPublisher.publishTo(Collections.singleton(123L), "topic3", mapValue);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic3", mapValue,
				Collections.singleton(123L), null);
	}

	@Test
	public void testPublishToAllExcept() {
		this.wampPublisher.publishToAllExcept(Collections.singleton(123L), "topic", 1);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic", Collections.singletonList(1), null,
				Collections.singleton(123L));
	}

	@Test
	public void testPublishToAllExceptList() {
		List<String> value = Arrays.asList("a", "b", "c");
		this.wampPublisher.publishToAllExcept(Collections.singleton(123L), "topic2",
				value);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());
		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic2", value, null,
				Collections.singleton(123L));
	}

	@Test
	public void testPublishToAllExceptMap() {
		Map<String, Integer> mapValue = new HashMap<>();
		mapValue.put("one", 1);
		mapValue.put("two", 2);
		mapValue.put("three", 3);
		this.wampPublisher.publishToAllExcept(Collections.singleton(123L), "topic3",
				mapValue);
		Mockito.verify(this.clientOutboundChannel, Mockito.times(1))
				.send(this.messageCaptor.capture());

		PublishMessage publishMessage = this.messageCaptor.getValue();
		assertPublishMessage(publishMessage, "topic3", mapValue, null,
				Collections.singleton(123L));
	}

	private static <T> void assertPublishMessage(PublishMessage publishMessage,
			String topic, Map<String, T> value, Set<Long> eligible, Set<Long> exclude) {
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isGreaterThan(0);
		assertThat(publishMessage.getTopic()).isEqualTo(topic);
		if (eligible == null) {
			assertThat(publishMessage.getEligible()).isNull();
		}
		else {
			assertThat(publishMessage.getEligible()).containsOnlyElementsOf(eligible);
		}
		if (exclude == null) {
			assertThat(publishMessage.getExclude()).isNull();
		}
		else {
			assertThat(publishMessage.getExclude()).containsOnlyElementsOf(exclude);
		}
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).isNull();
		assertThat(publishMessage.getArgumentsKw()).containsAllEntriesOf(value);
	}

	private static <T> void assertPublishMessage(PublishMessage publishMessage,
			String topic, List<T> values, Set<Long> eligible, Set<Long> exclude) {
		assertThat(publishMessage.getCode()).isEqualTo(16);
		assertThat(publishMessage.getRequestId()).isGreaterThan(0);
		assertThat(publishMessage.getTopic()).isEqualTo(topic);
		if (eligible == null) {
			assertThat(publishMessage.getEligible()).isNull();
		}
		else {
			assertThat(publishMessage.getEligible()).containsOnlyElementsOf(eligible);
		}
		if (exclude == null) {
			assertThat(publishMessage.getExclude()).isNull();
		}
		else {
			assertThat(publishMessage.getExclude()).containsOnlyElementsOf(exclude);
		}
		assertThat(publishMessage.isAcknowledge()).isFalse();
		assertThat(publishMessage.isDiscloseMe()).isFalse();
		assertThat(publishMessage.isExcludeMe()).isTrue();
		assertThat(publishMessage.getArguments()).containsExactlyElementsOf(values);
		assertThat(publishMessage.getArgumentsKw()).isNull();
	}

}
