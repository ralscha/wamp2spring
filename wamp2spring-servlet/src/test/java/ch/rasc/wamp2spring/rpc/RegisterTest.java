/**
 * Copyright 2018-2018 the original author or authors.
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
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.RegisteredMessage;
import ch.rasc.wamp2spring.message.ResultMessage;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.servlet.config.EnableServletWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = RegisterTest.Config.class)
public class RegisterTest extends BaseWampTest {

	@Test
	public void testCallRegisteredProcedure() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.MSGPACK);
				WampClient wc2 = new WampClient(DataFormat.JSON)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			RegisterMessage registerMessage = new RegisterMessage(1, "divide");
			RegisteredMessage registeredMessage = wc1
					.sendMessageWithResult(registerMessage);

			assertThat(registeredMessage.getRequestId())
					.isEqualTo(registerMessage.getRequestId());
			long registrationId = registeredMessage.getRegistrationId();

			CallMessage callMessage = new CallMessage(2, "divide", Arrays.asList(10, 5));
			wc2.sendMessage(callMessage);

			InvocationMessage invocationMessage = wc1.getWampMessage();
			assertThat(invocationMessage.getRegistrationId()).isEqualTo(registrationId);
			assertThat(invocationMessage.getArgumentsKw()).isNull();
			assertThat(invocationMessage.getArguments()).containsExactly(10, 5);

			YieldMessage yieldMessage = new YieldMessage(invocationMessage.getRequestId(),
					Collections.singletonList(2), null);
			wc1.sendMessage(yieldMessage);

			ResultMessage result = wc2.getWampMessage();
			wc1.waitForNothing();

			assertThat(result.getRequestId()).isEqualTo(2);
			assertThat(result.getArguments()).containsExactly(2);
			assertThat(result.getArgumentsKw()).isNull();

			wc1.close();
			TimeUnit.SECONDS.sleep(2);
			callMessage = new CallMessage(3, "divide", Arrays.asList(20, 10));
			ErrorMessage error = wc2.sendMessageWithResult(callMessage);
			assertThat(error.getType()).isEqualTo(CallMessage.CODE);
			assertThat(error.getRequestId()).isEqualTo(3);
			assertThat(error.getError())
					.isEqualTo(WampError.NO_SUCH_PROCEDURE.getExternalValue());
			assertThat(error.getArguments()).isNull();
			assertThat(error.getArgumentsKw()).isNull();
		}
	}

	@Test
	public void testInvocationError() throws Exception {
		try (WampClient wc1 = new WampClient(DataFormat.MSGPACK);
				WampClient wc2 = new WampClient(DataFormat.JSON)) {
			wc1.connect(wampEndpointUrl());
			wc2.connect(wampEndpointUrl());

			RegisterMessage registerMessage = new RegisterMessage(1, "multiply");
			RegisteredMessage registeredMessage = wc1
					.sendMessageWithResult(registerMessage);

			assertThat(registeredMessage.getRequestId())
					.isEqualTo(registerMessage.getRequestId());
			long registrationId = registeredMessage.getRegistrationId();

			CallMessage callMessage = new CallMessage(22, "multiply",
					Arrays.asList(10, 5));
			wc2.sendMessage(callMessage);

			InvocationMessage invocationMessage = wc1.getWampMessage();
			assertThat(invocationMessage.getRegistrationId()).isEqualTo(registrationId);
			assertThat(invocationMessage.getArgumentsKw()).isNull();
			assertThat(invocationMessage.getArguments()).containsExactly(10, 5);

			ErrorMessage invocationError = new ErrorMessage(invocationMessage,
					WampError.INVALID_ARGUMENT);
			wc1.sendMessage(invocationError);

			ErrorMessage callError = wc2.getWampMessage();
			wc1.waitForNothing();

			assertThat(callError.getType()).isEqualTo(CallMessage.CODE);
			assertThat(callError.getRequestId()).isEqualTo(22);
			assertThat(callError.getError())
					.isEqualTo(WampError.INVALID_ARGUMENT.getExternalValue());
			assertThat(callError.getArguments()).isNull();
			assertThat(callError.getArgumentsKw()).isNull();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableServletWamp
	static class Config {
		// nothing here
	}

}
