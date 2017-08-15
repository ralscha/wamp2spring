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
package ch.rasc.wampspring.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;

import ch.rasc.wampspring.config.EnableWamp;
import ch.rasc.wampspring.message.CallMessage;
import ch.rasc.wampspring.message.ErrorMessage;
import ch.rasc.wampspring.message.ResultMessage;
import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.testsupport.BaseWampTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = CallTest.Config.class)
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
				new CallMessage(5L, "callService.call"), Protocol.JSON);
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
				new CallMessage(6L, "callService.noParams"), Protocol.MSGPACK);
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
				Protocol.CBOR);
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
				Protocol.MSGPACK);
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
				Protocol.JSON);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(9L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("HI/the_second_argument");
		assertThat(this.callService.isCalled("callWithDtoAndMessage")).isTrue();
	}

	@SpringBootApplication
	@EnableWamp
	static class Config {

		@Bean
		public CallService callService() {
			return new CallService();
		}

	}

}
