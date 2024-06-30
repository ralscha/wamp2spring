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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketSession;

import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.CompletableFutureWebSocketHandler;
import ch.rasc.wamp2spring.testsupport.Maps;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = CallParameterTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
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
	public void testSessionIdAnnotation() throws Exception {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();

		try (WebSocketSession wsSession = startWebSocketSession(result,
				DataFormat.SMILE)) {
			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(DataFormat.SMILE, wsSession, helloMessage);

			WelcomeMessage welcomeMessage = result.getWelcomeMessage();

			CallMessage callMessage = new CallMessage(22L, "sessionIdAnnotation");
			sendMessage(DataFormat.SMILE, wsSession, callMessage);

			WampMessage wampResult = result.getWampMessage();

			assertThat(wampResult).isInstanceOf(ResultMessage.class);
			ResultMessage resultMessage = (ResultMessage) wampResult;
			assertThat(resultMessage.getRequestId()).isEqualTo(22L);
			assertThat(resultMessage.getArgumentsKw()).isNull();
			assertThat(resultMessage.getArguments())
					.containsExactly("session id: " + welcomeMessage.getSessionId());
		}
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
	@EnableReactiveWamp
	static class Config {

		@Bean
		public CallParameterTestService callParameterTestService() {
			return new CallParameterTestService();
		}

	}

}
