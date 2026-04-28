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
package ch.rasc.wamp2spring.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.smile.SmileMapper;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;
import ch.rasc.wamp2spring.util.MessagePackCodec;
import ch.rasc.wamp2spring.util.WampJson;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Disabled
public class BaseWampTest {

	public enum DataFormat {

		JSON, MSGPACK, CBOR, SMILE

	}

	protected final ObjectMapper jsonObjectMapper = WampJson.createJsonObjectMapper();

	protected final ObjectMapper msgpackObjectMapper = WampJson.createJsonObjectMapper();

	protected final ObjectMapper cborObjectMapper = new CBORMapper();

	protected final ObjectMapper smileObjectMapper = new SmileMapper();

	@LocalServerPort
	public int actualPort;

	protected WampMessage sendWampMessage(WampMessage msg)
			throws InterruptedException, ExecutionException, TimeoutException, IOException {
		return sendWampMessage(msg, DataFormat.JSON);
	}

	protected WampMessage sendWampMessage(WampMessage msg, DataFormat dataFormat)
			throws InterruptedException, ExecutionException, TimeoutException, IOException {
		CompletableFutureWebSocketHandler result = new CompletableFutureWebSocketHandler();
		WebSocketClient webSocketClient = new StandardWebSocketClient();

		try (WebSocketSession webSocketSession = webSocketClient
			.execute(result, getHeaders(dataFormat), wampEndpointUrl())
			.get()) {

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

	protected void sendMessage(DataFormat dataFormat, WebSocketSession webSocketSession, WampMessage msg)
			throws IOException {

		byte[] payload = serializeMessage(dataFormat, msg);
		if (dataFormat == DataFormat.MSGPACK || dataFormat == DataFormat.CBOR || dataFormat == DataFormat.SMILE) {
			webSocketSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(payload)));
		}
		else {
			webSocketSession.sendMessage(new TextMessage(payload));
		}
	}

	protected byte[] serializeMessage(DataFormat dataFormat, WampMessage msg) throws IOException {
		ObjectMapper objectMapper = this.jsonObjectMapper;
		if (dataFormat == DataFormat.CBOR) {
			objectMapper = this.cborObjectMapper;
		}
		else if (dataFormat == DataFormat.SMILE) {
			objectMapper = this.smileObjectMapper;
		}

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = objectMapper.createGenerator(bos)) {
			generator.writeStartArray();
			msg.serialize(generator);
			generator.writeEndArray();
			generator.close();

			byte[] payload = bos.toByteArray();
			if (dataFormat == DataFormat.MSGPACK) {
				return MessagePackCodec.fromJson(payload, this.msgpackObjectMapper);
			}
			return payload;
		}
	}

	protected WebSocketSession startWebSocketSession(AbstractWebSocketHandler result, DataFormat dataFormat)
			throws InterruptedException, ExecutionException {
		WebSocketClient webSocketClient = new StandardWebSocketClient();
		return webSocketClient.execute(result, getHeaders(dataFormat), wampEndpointUrl()).get();
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
		return UriComponentsBuilder.fromUriString("ws://localhost:" + this.actualPort + "/wamp")
			.build()
			.encode()
			.toUri();
	}

}
