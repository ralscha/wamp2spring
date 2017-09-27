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

package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.config.EnableWamp;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.rpc.TestDto;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ClientToServerTest.Config.class)
public class ClientToServerTest extends BaseWampTest {

	@Autowired
	private ClientToServerService clientToServerService;

	@Test
	public void testDefaultListener() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(1L,
					"clientToServerService.sum").arguments(Arrays.asList(1, 2)).build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("sum")).isTrue();
		}
	}

	@Test
	public void testValueListener() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(2L, "sum2")
					.arguments(Arrays.asList(3, 4)).build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("sum2")).isTrue();
		}
	}

	@Test
	public void testTopicListener() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(3L, "sum3")
					.arguments(Arrays.asList(5, 6)).build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("sum3")).isTrue();
		}
	}

	@Test
	public void testMessageArgument() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(4L,
					"clientToServerService.listener").arguments(Arrays.asList(8, 9))
							.build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("listener")).isTrue();
		}
	}

	@Test
	public void testNoArguments() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(5L,
					"clientToServerService.noParams").build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("noParams")).isTrue();
		}
	}

	@Test
	public void testError() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(6L,
					"clientToServerService.error").addArgument("theArgument").build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("error")).isTrue();
		}
	}

	@Test
	public void testDto() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			TestDto testDto = new TestDto(1, "Hi");
			PublishMessage publishMessage = new PublishMessage.Builder(7L,
					"clientToServerService.listenerWithDto").addArgument(testDto).build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("listenerWithDto")).isTrue();
		}
	}

	@Test
	public void testDtoAndMessage() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			TestDto testDto = new TestDto(2, "Hi");
			PublishMessage publishMessage = new PublishMessage.Builder(8L,
					"clientToServerService.listenerWithDtoAndMessage")
							.addArgument(testDto).addArgument("the_second_argument")
							.build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("listenerWithDtoAndMessage"))
					.isTrue();
		}
	}

	@Test
	public void testPrefix() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(9L,
					"news.business").addArgument(23).build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("prefixListener")).isTrue();

			publishMessage = new PublishMessage.Builder(10L, "news.sport").addArgument(23)
					.build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("prefixListener")).isTrue();

			publishMessage = new PublishMessage.Builder(10L, "new.world").addArgument(23)
					.build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("prefixListener")).isFalse();
		}
	}

	@Test
	public void testWildcard() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.MSGPACK)) {
			wc.connect(wampEndpointUrl());

			PublishMessage publishMessage = new PublishMessage.Builder(9L,
					"crud.user.create").addArgument("111").build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("crudCreateListener"))
					.isTrue();

			publishMessage = new PublishMessage.Builder(10L, "crud.company.create")
					.addArgument("111").build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("crudCreateListener"))
					.isTrue();

			publishMessage = new PublishMessage.Builder(10L, "crud.user.update")
					.addArgument("111").build();
			wc.sendMessage(publishMessage);
			wc.waitForNothing();

			assertThat(this.clientToServerService.isCalled("crudCreateListener"))
					.isFalse();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableWamp
	static class Config {
		@Bean
		public ClientToServerService clientToServerService() {
			return new ClientToServerService();
		}
	}

}
