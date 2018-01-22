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
package ch.rasc.wamp2spring.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.servlet.config.WampSubProtocolHandler;
import ch.rasc.wamp2spring.testsupport.BaseWampTest.DataFormat;

public class WampClient implements AutoCloseable {
	private final CompletableFutureWebSocketHandler result;

	private WebSocketSession webSocketSession;

	private final JsonFactory jsonFactory;

	private long wampSessionId;

	private final boolean isBinary;

	private final WebSocketHttpHeaders headers;

	public WampClient(DataFormat dataFormat) {
		this.isBinary = dataFormat != DataFormat.JSON;
		this.result = new CompletableFutureWebSocketHandler();
		this.headers = new WebSocketHttpHeaders();

		switch (dataFormat) {
		case CBOR:
			this.jsonFactory = new ObjectMapper(new CBORFactory()).getFactory();
			this.headers.setSecWebSocketProtocol(WampSubProtocolHandler.CBOR_PROTOCOL);
			break;
		case MSGPACK:
			this.jsonFactory = new ObjectMapper(new MessagePackFactory()).getFactory();
			this.headers.setSecWebSocketProtocol(WampSubProtocolHandler.MSGPACK_PROTOCOL);
			break;
		case JSON:
			this.jsonFactory = new MappingJsonFactory(new ObjectMapper());
			this.headers.setSecWebSocketProtocol(WampSubProtocolHandler.JSON_PROTOCOL);
			break;
		case SMILE:
			this.jsonFactory = new ObjectMapper(new SmileFactory()).getFactory();
			this.headers.setSecWebSocketProtocol(WampSubProtocolHandler.SMILE_PROTOCOL);
			break;
		default:
			this.jsonFactory = null;
		}

	}

	public void connect(URI wampEndpointUrl) throws InterruptedException,
			ExecutionException, IOException, TimeoutException {
		List<WampRole> roles = new ArrayList<>();
		roles.add(new WampRole("publisher"));
		roles.add(new WampRole("subscriber"));
		roles.add(new WampRole("caller"));
		HelloMessage helloMessage = new HelloMessage("realm", roles);

		WebSocketClient webSocketClient = new StandardWebSocketClient();
		this.webSocketSession = webSocketClient
				.doHandshake(this.result, this.headers, wampEndpointUrl).get();

		sendMessage(helloMessage);
		WelcomeMessage welcomeMessage = this.result.getWelcomeMessage();
		this.result.reset();
		this.wampSessionId = welcomeMessage.getSessionId();
	}

	public void sendMessage(WampMessage msg) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = this.jsonFactory.createGenerator(bos)) {
			generator.writeStartArray();

			msg.serialize(generator);
			generator.writeEndArray();
			generator.close();

			if (this.isBinary) {
				this.webSocketSession.sendMessage(new BinaryMessage(bos.toByteArray()));
			}
			else {
				this.webSocketSession.sendMessage(new TextMessage(bos.toByteArray()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends WampMessage> T sendMessageWithResult(WampMessage msg)
			throws IOException, InterruptedException, ExecutionException,
			TimeoutException {
		sendMessage(msg);
		T wampMessage = (T) this.result.getWampMessage();
		this.result.reset();
		return wampMessage;
	}

	public <T extends WampMessage> T getWampMessage()
			throws InterruptedException, ExecutionException, TimeoutException {
		@SuppressWarnings("unchecked")
		T wampMessage = (T) this.result.getWampMessage();
		this.result.reset();
		return wampMessage;
	}

	public void waitForNothing() {
		try {
			this.result.getWampMessages();
			Assert.fail("has to fail with a timeout exception");
		}
		catch (Exception e) {
			assertThat(e).isInstanceOf(TimeoutException.class);
		}
	}

	public CompletableFutureWebSocketHandler getResult() {
		return this.result;
	}

	public long getWampSessionId() {
		return this.wampSessionId;
	}

	@Override
	public void close() throws Exception {
		if (this.webSocketSession != null) {
			this.webSocketSession.close();
		}
	}

}
