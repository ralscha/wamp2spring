/**
 * Copyright 2017-2021 the original author or authors.
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

import java.util.Arrays;

import org.assertj.core.data.MapEntry;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.servlet.EnableServletWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.Maps;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = WampResultTest.Config.class)
public class WampResultTest extends BaseWampTest {

	@Autowired
	private WampResultService wampResultService;

	@Test
	public void testArgumentOne() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(1L, "sum", Arrays.asList(6, 9)));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(1L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(15);

		assertThat(this.wampResultService.isCalled("sum")).isTrue();
	}

	@Test
	public void testArgumentTwo() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(2L, "two", Arrays.asList("ralph", "hplar")));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(2L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("RALPH", "HPLAR");

		assertThat(this.wampResultService.isCalled("two")).isTrue();
	}

	@Test
	public void testEmpty() throws Exception {
		WampMessage receivedMessage = sendWampMessage(new CallMessage(3L, "empty"),
				DataFormat.CBOR);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(3L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).isNull();

		assertThat(this.wampResultService.isCalled("empty")).isTrue();
	}

	@Test
	public void testArgumentTwoKw() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(4L, "twoKw", Arrays.asList("time", "emit")),
				DataFormat.CBOR);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(4L);
		assertThat(result.getArgumentsKw()).containsOnly(MapEntry.entry("1", "TIME"),
				MapEntry.entry("2", "EMIT"));
		assertThat(result.getArguments()).isEmpty();

		assertThat(this.wampResultService.isCalled("twoKw")).isTrue();
	}

	@Test
	public void testMix() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(5L, "mix",
						Maps.map("amount", "123.25").map("text", "cookie").getMap()),
				DataFormat.MSGPACK);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(5L);
		assertThat(result.getArgumentsKw()).containsOnly(MapEntry.entry("5%", 129.4),
				MapEntry.entry("10%", 135.6));
		assertThat(result.getArguments()).containsExactly("c", "o", "o");

		assertThat(this.wampResultService.isCalled("mixedResult")).isTrue();
	}

	@Test
	public void testDto() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(6L, "toDto", Arrays.asList(123L, "name")));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(6L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments())
				.containsExactly(Maps.map("id", 123).map("name", "name").getMap());

		assertThat(this.wampResultService.isCalled("toDto")).isTrue();
	}

	@Test
	public void testTwoDto() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(7L, "toTwoDto", Arrays.asList(123L, "name")));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(7L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsOnly(
				Maps.map("id", 123).map("name", "name").getMap(),
				Maps.map("id", 1123).map("name", "name").getMap());

		assertThat(this.wampResultService.isCalled("toTwoDto")).isTrue();
	}

	@Test
	public void testDtoKw() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(8L, "toDtoKw", Arrays.asList(1234L, "joe")));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(8L);
		assertThat(result.getArgumentsKw()).containsOnly(MapEntry.entry("id", 1234),
				MapEntry.entry("name", "joe"));
		assertThat(result.getArguments()).isEmpty();

		assertThat(this.wampResultService.isCalled("toDtoKw")).isTrue();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableServletWamp
	static class Config {

		@Bean
		public WampResultService wampResultService() {
			return new WampResultService();
		}

	}

}
