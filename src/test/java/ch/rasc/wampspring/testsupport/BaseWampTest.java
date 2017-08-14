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
package ch.rasc.wampspring.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rasc.wampspring.config.WampSubProtocolHandler;
import ch.rasc.wampspring.message.HelloMessage;
import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.message.WampRole;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Ignore
public class BaseWampTest {

	protected final JsonFactory jsonFactory = new MappingJsonFactory(new ObjectMapper());

	protected final JsonFactory msgpackFactory = new ObjectMapper(
			new MessagePackFactory()).getFactory();

	@LocalServerPort
	public int actualPort;

	protected WampMessage sendWampMessage(WampMessage msg) throws InterruptedException,
			ExecutionException, TimeoutException, IOException {
		return sendWampMessage(msg, false);
	}

	protected WampMessage sendWampMessage(WampMessage msg, boolean isMsgPack)
			throws InterruptedException, ExecutionException, TimeoutException,
			IOException {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler(
				this.jsonFactory, this.msgpackFactory);
		WebSocketClient webSocketClient = createWebSocketClient();

		try (WebSocketSession webSocketSession = webSocketClient
				.doHandshake(result, getHeaders(isMsgPack), wampEndpointUrl()).get()) {

			JsonFactory useFactory = this.jsonFactory;
			if (isMsgPack) {
				useFactory = this.msgpackFactory;
			}

			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("publisher"));
			roles.add(new WampRole("subscriber"));
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(isMsgPack, webSocketSession, useFactory, helloMessage);

			result.getWelcomeMessage();

			sendMessage(isMsgPack, webSocketSession, useFactory, msg);

			return result.getWampMessage();
		}
	}

	private static void sendMessage(boolean isMsgPack, WebSocketSession webSocketSession,
			JsonFactory useFactory, WampMessage msg) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = useFactory.createGenerator(bos)) {
			generator.writeStartArray();

			msg.serialize(generator);
			generator.writeEndArray();
			generator.close();

			if (isMsgPack) {
				webSocketSession.sendMessage(new BinaryMessage(bos.toByteArray()));
			}
			else {
				webSocketSession.sendMessage(new TextMessage(bos.toByteArray()));
			}
		}
	}

	protected WebSocketClient createWebSocketClient() {
		return new StandardWebSocketClient();
	}

	protected WebSocketSession startWebSocketSession(AbstractWebSocketHandler result,
			boolean isMsgPack) throws InterruptedException, ExecutionException {
		WebSocketClient webSocketClient = createWebSocketClient();
		return webSocketClient
				.doHandshake(result, getHeaders(isMsgPack), wampEndpointUrl()).get();
	}

	protected WebSocketHttpHeaders getHeaders(boolean isMsgPack) {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		if (isMsgPack) {
			headers.setSecWebSocketProtocol(WampSubProtocolHandler.MSGPACK_PROTOCOL);
		}
		else {
			headers.setSecWebSocketProtocol(WampSubProtocolHandler.JSON_PROTOCOL);
		}
		return headers;
	}

	protected URI wampEndpointUrl() {
		return UriComponentsBuilder
				.fromUriString("ws://localhost:" + this.actualPort + "/wamp").build()
				.encode().toUri();
	}

}
