package ch.rasc.wamp2spring.config;

import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;
import ch.rasc.wamp2spring.testsupport.WampClient;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ConnectionTest.Config.class)
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
	@EnableWamp
	static class Config {
		// nothing here
	}
}
