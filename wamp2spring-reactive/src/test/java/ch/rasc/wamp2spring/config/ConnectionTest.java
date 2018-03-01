/**
 * Copyright 2017-2018 the original author or authors.
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
package ch.rasc.wamp2spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.reactive.EnableReactiveWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ConnectionTest.Config.class)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
public class ConnectionTest extends BaseWampTest {

	@Test
	public void secondHelloMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			// send hello message after session is established. this should close
			// the connection
			wc.sendMessage(new HelloMessage("theRealm", Collections.EMPTY_LIST));

			wc.waitForNothing();
			try {
				wc.sendMessage(
						new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	public void sendAbortMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			wc.sendMessage(new AbortMessage(WampError.NETWORK_FAILURE));

			wc.waitForNothing();
			try {
				wc.sendMessage(
						new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	public void sendGoodbyeMessageTest() throws Exception {
		try (WampClient wc = new WampClient(DataFormat.JSON)) {

			wc.connect(wampEndpointUrl());

			GoodbyeMessage goodbyeMessage = wc
					.sendMessageWithResult(new GoodbyeMessage(WampError.NOT_AUTHORIZED));
			assertThat(goodbyeMessage.getCode()).isEqualTo(6);
			assertThat(goodbyeMessage.getMessage()).isNull();
			assertThat(goodbyeMessage.getReason())
					.isEqualTo(WampError.GOODBYE_AND_OUT.getExternalValue());

			try {
				TimeUnit.SECONDS.sleep(5);
				wc.sendMessage(
						new PublishMessage.Builder(1L, "crud.user.create").build());
				fail("sendMessage should fail because the connection should be closed");
			}
			catch (Exception e) {
				assertThat(e).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableReactiveWamp
	static class Config {
		// nothing here
	}
}
