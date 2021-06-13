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
import java.util.Collections;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = CallTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
public class CallTest extends BaseWampTest {

	@Autowired
	private CallService callService;

	@Test
	public void testReturnValue() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(1L, "callService.sum", Arrays.asList(3, 4)));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(1L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(7);

		assertThat(this.callService.isCalled("sum")).isTrue();
	}

	@Test
	public void testDifferentProcedureName() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(2L, "sum2", Arrays.asList(11, 22)));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(2L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(33);
		assertThat(this.callService.isCalled("sumDifferent1")).isTrue();

		receivedMessage = sendWampMessage(
				new CallMessage(3L, "sum3", Arrays.asList(4, 5)));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(3L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(9);
		assertThat(this.callService.isCalled("sumDifferent2")).isTrue();
	}

	@Test
	public void testNoReturnValue() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(4L, "callService.noReturn", Arrays.asList("name", 23)));
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(4L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).isNull();
		assertThat(this.callService.isCalled("noReturn")).isTrue();
	}

	@Test
	public void testWrongNumberOfArguments() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(44L, "callService.noReturn", Arrays.asList("name")));
		assertThat(receivedMessage).isInstanceOf(ErrorMessage.class);
		ErrorMessage error = (ErrorMessage) receivedMessage;
		assertThat(error.getRequestId()).isEqualTo(44L);
		assertThat(error.getArgumentsKw()).isNull();
		assertThat(error.getArguments()).isNull();
		assertThat(error.getError()).isEqualTo("wamp.error.invalid_argument");
		assertThat(this.callService.isCalled("noReturn")).isFalse();
	}

	@Test
	public void testCallMessageArgument() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(5L, "callService.call"), DataFormat.JSON);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(5L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(5);
		assertThat(this.callService.isCalled("call")).isTrue();
	}

	@Test
	public void testNoParams() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(6L, "callService.noParams"), DataFormat.CBOR);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(6L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("nothing here");
		assertThat(this.callService.isCalled("noParams")).isTrue();
	}

	@Test
	public void testException() throws Exception {
		WampMessage receivedMessage = sendWampMessage(new CallMessage(7L,
				"callService.error", Collections.singletonList("theArgument")),
				DataFormat.CBOR);
		assertThat(receivedMessage).isInstanceOf(ErrorMessage.class);
		ErrorMessage error = (ErrorMessage) receivedMessage;
		assertThat(error.getRequestId()).isEqualTo(7L);
		assertThat(error.getArgumentsKw()).isNull();
		assertThat(error.getArguments()).isNull();
		assertThat(error.getError()).isEqualTo("wamp.error.invalid_argument");
		assertThat(this.callService.isCalled("error")).isTrue();
	}

	@Test
	public void testDto() throws Exception {
		TestDto dto = new TestDto(1, "Hi");
		WampMessage receivedMessage = sendWampMessage(new CallMessage(8L,
				"callService.callWithDto", Collections.singletonList(dto)),
				DataFormat.MSGPACK);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(8L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("HI");
		assertThat(this.callService.isCalled("callWithDto")).isTrue();
	}

	@Test
	public void testDtoAndCallMessageArgument() throws Exception {
		TestDto dto = new TestDto(2, "Hi");
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(9L, "callService.callWithDtoAndMessage",
						Arrays.asList(dto, "the_second_argument")),
				DataFormat.JSON);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(9L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("HI/the_second_argument");
		assertThat(this.callService.isCalled("callWithDtoAndMessage")).isTrue();
	}

	@Test
	public void testReturnedList() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(9L, "callService.callAndReturnList"), DataFormat.JSON);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(9L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly(1.1, 2.2, 3.3);
		assertThat(this.callService.isCalled("callAndReturnList")).isTrue();
	}

	@Test
	public void testReturnedMap() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(9L, "callService.callAndReturnMap"), DataFormat.JSON);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(9L);
		assertThat(result.getArgumentsKw()).hasSize(2).containsEntry("0.0", 1.0)
				.containsEntry("1.0", 2.0);
		assertThat(result.getArguments()).isEmpty();
		assertThat(this.callService.isCalled("callAndReturnMap")).isTrue();
	}

	@Test
	public void testCallWithException() throws Exception {
		WampMessage receivedMessage = sendWampMessage(
				new CallMessage(10L, "callService.callWithException",
						Collections.singletonList("theArgument")),
				DataFormat.JSON);
		assertThat(receivedMessage).isInstanceOf(ErrorMessage.class);
		ErrorMessage result = (ErrorMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(10L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("arg1");
		assertThat(result.getError()).isEqualTo("the error message");
		assertThat(this.callService.isCalled("callWithException")).isTrue();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {

		@Bean
		public CallService callService() {
			return new CallService();
		}

	}

}
