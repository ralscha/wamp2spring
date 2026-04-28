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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.smile.SmileMapper;

import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.reactive.WampWebSocketHandler;
import ch.rasc.wamp2spring.testsupport.BaseWampTest.DataFormat;
import ch.rasc.wamp2spring.util.MessagePackCodec;
import ch.rasc.wamp2spring.util.WampJson;

public class WampClient implements AutoCloseable {

	private final CompletableFutureWebSocketHandler result;

	private WebSocketSession webSocketSession;

	private final ObjectMapper objectMapper;

	private final DataFormat dataFormat;

	private long wampSessionId;

	private final boolean isBinary;

	private final WebSocketHttpHeaders headers;

	public WampClient(DataFormat dataFormat) {
		this.dataFormat = dataFormat;
		this.isBinary = dataFormat != DataFormat.JSON;
		this.result = new CompletableFutureWebSocketHandler();
		this.headers = new WebSocketHttpHeaders();

		this.objectMapper = switch (dataFormat) {
			case CBOR -> new CBORMapper();
			case MSGPACK -> WampJson.createJsonObjectMapper();
			case JSON -> WampJson.createJsonObjectMapper();
			case SMILE -> new SmileMapper();
		};
		String protocol = switch (dataFormat) {
			case CBOR -> WampWebSocketHandler.CBOR_PROTOCOL;
			case MSGPACK -> WampWebSocketHandler.MSGPACK_PROTOCOL;
			case JSON -> WampWebSocketHandler.JSON_PROTOCOL;
			case SMILE -> WampWebSocketHandler.SMILE_PROTOCOL;
		};
		this.headers.setSecWebSocketProtocol(protocol);

	}

	public void connect(URI wampEndpointUrl)
			throws InterruptedException, ExecutionException, IOException, TimeoutException {
		List<WampRole> roles = new ArrayList<>();
		roles.add(new WampRole("publisher"));
		roles.add(new WampRole("subscriber"));
		roles.add(new WampRole("caller"));
		HelloMessage helloMessage = new HelloMessage(roles);

		WebSocketClient webSocketClient = new StandardWebSocketClient();
		this.webSocketSession = webSocketClient.execute(this.result, this.headers, wampEndpointUrl).get();

		sendMessage(helloMessage);
		WelcomeMessage welcomeMessage = this.result.getWelcomeMessage();
		this.result.reset();
		this.wampSessionId = welcomeMessage.getSessionId();
	}

	public void sendMessage(WampMessage msg) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = this.objectMapper.createGenerator(bos)) {
			generator.writeStartArray();

			msg.serialize(generator);
			generator.writeEndArray();
			generator.close();
			byte[] payload = bos.toByteArray();
			if (this.dataFormat == DataFormat.MSGPACK) {
				payload = MessagePackCodec.fromJson(payload, this.objectMapper);
			}

			if (this.isBinary) {
				this.webSocketSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(payload)));
			}
			else {
				this.webSocketSession.sendMessage(new TextMessage(payload));
			}
		}
	}

	@SuppressWarnings({ "TypeParameterUnusedInFormals", "unchecked" })
	public <T extends WampMessage> T sendMessageWithResult(WampMessage msg)
			throws IOException, InterruptedException, ExecutionException, TimeoutException {
		sendMessage(msg);
		T wampMessage = (T) this.result.getWampMessage();
		this.result.reset();
		return wampMessage;
	}

	@SuppressWarnings("TypeParameterUnusedInFormals")
	public <T extends WampMessage> T getWampMessage()
			throws InterruptedException, ExecutionException, TimeoutException {
		@SuppressWarnings("unchecked")
		T wampMessage = (T) this.result.getWampMessage();
		this.result.reset();
		return wampMessage;
	}

	public void waitForNothing() {
		try {
			this.result.waitForNoMessage();
			Assertions.fail("has to fail with a timeout exception");
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
