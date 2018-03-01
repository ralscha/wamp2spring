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
package ch.rasc.wamp2spring.testsupport;

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
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Ignore
public class BaseWampTest {

	public enum DataFormat {
		JSON, MSGPACK, CBOR, SMILE
	}

	protected final JsonFactory jsonFactory = new MappingJsonFactory(new ObjectMapper());

	protected final JsonFactory msgpackFactory = new ObjectMapper(
			new MessagePackFactory()).getFactory();

	protected final JsonFactory cborFactory = new ObjectMapper(new CBORFactory())
			.getFactory();

	protected final JsonFactory smileFactory = new ObjectMapper(new SmileFactory())
			.getFactory();

	@LocalServerPort
	public int actualPort;

	protected WampMessage sendWampMessage(WampMessage msg) throws InterruptedException,
			ExecutionException, TimeoutException, IOException {
		return sendWampMessage(msg, DataFormat.JSON);
	}

	protected WampMessage sendWampMessage(WampMessage msg, DataFormat dataFormat)
			throws InterruptedException, ExecutionException, TimeoutException,
			IOException {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		WebSocketClient webSocketClient = new StandardWebSocketClient();

		try (WebSocketSession webSocketSession = webSocketClient
				.doHandshake(result, getHeaders(dataFormat), wampEndpointUrl()).get()) {

			List<WampRole> roles = new ArrayList<>();
			roles.add(new WampRole("publisher"));
			roles.add(new WampRole("subscriber"));
			roles.add(new WampRole("caller"));
			HelloMessage helloMessage = new HelloMessage("realm", roles);
			sendMessage(dataFormat, webSocketSession, helloMessage);

			result.getWelcomeMessage();

			sendMessage(dataFormat, webSocketSession, msg);

			return result.getWampMessage();
		}
	}

	protected void sendMessage(DataFormat dataFormat, WebSocketSession webSocketSession,
			WampMessage msg) throws IOException {

		JsonFactory useFactory = this.jsonFactory;
		if (dataFormat == DataFormat.MSGPACK) {
			useFactory = this.msgpackFactory;
		}
		else if (dataFormat == DataFormat.CBOR) {
			useFactory = this.cborFactory;
		}
		else if (dataFormat == DataFormat.SMILE) {
			useFactory = this.smileFactory;
		}

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = useFactory.createGenerator(bos)) {
			generator.writeStartArray();

			msg.serialize(generator);
			generator.writeEndArray();
			generator.close();

			if (dataFormat == DataFormat.MSGPACK || dataFormat == DataFormat.CBOR
					|| dataFormat == DataFormat.SMILE) {
				webSocketSession.sendMessage(new BinaryMessage(bos.toByteArray()));
			}
			else {
				webSocketSession.sendMessage(new TextMessage(bos.toByteArray()));
			}
		}
	}

	protected WebSocketSession startWebSocketSession(AbstractWebSocketHandler result,
			DataFormat dataFormat) throws InterruptedException, ExecutionException {
		WebSocketClient webSocketClient = new StandardWebSocketClient();
		return webSocketClient
				.doHandshake(result, getHeaders(dataFormat), wampEndpointUrl()).get();
	}

	protected WebSocketHttpHeaders getHeaders(DataFormat dataFormat) {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

		if (dataFormat == DataFormat.MSGPACK) {
			headers.setSecWebSocketProtocol(WampSubProtocolHandler.MSGPACK_PROTOCOL);
		}
		else if (dataFormat == DataFormat.SMILE) {
			headers.setSecWebSocketProtocol(WampSubProtocolHandler.SMILE_PROTOCOL);
		}
		else if (dataFormat == DataFormat.CBOR) {
			headers.setSecWebSocketProtocol(WampSubProtocolHandler.CBOR_PROTOCOL);
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
