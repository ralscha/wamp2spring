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

import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wampspring.config.EnableWamp;
import ch.rasc.wampspring.message.CallMessage;
import ch.rasc.wampspring.message.ResultMessage;
import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.testsupport.BaseWampTest;
import ch.rasc.wampspring.testsupport.Maps;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = CallParameterTest.Config.class)
public class CallParameterTest extends BaseWampTest {

	@Test
	public void testHeaderMethod() throws Exception {
		CallMessage callMessage = new CallMessage(1L, "headerMethod");
		WampMessage receivedMessage = sendWampMessage(callMessage);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(1L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).hasSize(1);
		assertThat((String) result.getArguments().get(0))
				.startsWith("headerMethod called: ");
	}

	@Test
	public void testHeadersMethod() throws Exception {
		CallMessage callMessage = new CallMessage(2L, "headersMethod");
		WampMessage receivedMessage = sendWampMessage(callMessage);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(2L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("headersMethod called");
	}

	@Test
	public void testMessageMethod() throws Exception {
		CallMessage callMessage = new CallMessage(3L, "messageMethod");
		WampMessage receivedMessage = sendWampMessage(callMessage);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(3L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("messageMethod called: 3");
	}

	@Test
	public void testMix() throws Exception {
		CallMessage callMessage = new CallMessage(4L, "mix", Maps.map("param1", "param1")
				.map("param2", 2).map("param3", 3.3f).map("param4", "param4").getMap());
		WampMessage receivedMessage = sendWampMessage(callMessage);
		assertThat(receivedMessage).isInstanceOf(ResultMessage.class);
		ResultMessage result = (ResultMessage) receivedMessage;
		assertThat(result.getRequestId()).isEqualTo(4L);
		assertThat(result.getArgumentsKw()).isNull();
		assertThat(result.getArguments()).containsExactly("mix");
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableWamp
	static class Config {

		@Bean
		public CallParameterTestService callParameterTestService() {
			return new CallParameterTestService();
		}

	}

}
