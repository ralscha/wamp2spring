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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.smile.SmileMapper;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.reactive.WampWebSocketHandler;
import ch.rasc.wamp2spring.util.MessagePackCodec;
import ch.rasc.wamp2spring.util.WampJson;

public class CompletableFutureWebSocketHandler extends AbstractWebSocketHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private final CompletableFuture<WelcomeMessage> welcomeMessageFuture;

	private CompletableFuture<List<WampMessage>> messageFuture;

	private final ObjectMapper jsonObjectMapper;

	private final ObjectMapper msgpackObjectMapper;

	private final ObjectMapper cborObjectMapper;

	private final ObjectMapper smileObjectMapper;

	private int noOfResults;

	private final int timeout;

	private final long noMessageTimeoutMillis;

	private final long settleTimeMillis;

	private List<WampMessage> receivedMessages;

	public CompletableFutureWebSocketHandler() {
		this(1);
	}

	public CompletableFutureWebSocketHandler(int expectedNoOfResults) {
		this.jsonObjectMapper = WampJson.createJsonObjectMapper();
		this.msgpackObjectMapper = WampJson.createJsonObjectMapper();
		this.cborObjectMapper = new CBORMapper();
		this.smileObjectMapper = new SmileMapper();
		this.timeout = getTimeoutValue();
		this.noMessageTimeoutMillis = getTimeoutValue("WS_NO_MESSAGE_TIMEOUT_MILLIS", 250L);
		this.settleTimeMillis = getTimeoutValue("WS_SETTLE_TIMEOUT_MILLIS", 250L);
		this.welcomeMessageFuture = new CompletableFuture<>();
		this.reset(expectedNoOfResults);
	}

	private static int getTimeoutValue() {
		return (int) getTimeoutValue("WS_TIMEOUT", 2L);
	}

	private static long getTimeoutValue(String envVariableName, long defaultValue) {
		long timeout = defaultValue;
		try {
			String timeoutValue = System.getenv(envVariableName);
			timeout = Long.parseLong(timeoutValue);
		}
		catch (Exception e) {
			// ignore error
		}
		return timeout;
	}

	public void reset() {
		reset(1);
	}

	public void reset(int expectedNoOfResults) {
		this.noOfResults = expectedNoOfResults;
		this.messageFuture = new CompletableFuture<>();
		this.receivedMessages = new ArrayList<>();
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		try {
			WampMessage wampMessage = WampMessage.deserialize(this.jsonObjectMapper, message.asBytes());

			if (wampMessage instanceof WelcomeMessage welcomeMessage) {
				this.welcomeMessageFuture.complete(welcomeMessage);
			}
			else {
				this.receivedMessages.add(wampMessage);
				if (this.receivedMessages.size() == this.noOfResults) {
					this.messageFuture.complete(this.receivedMessages);
				}

			}

		}
		catch (IOException e) {
			this.welcomeMessageFuture.completeExceptionally(e);
			this.messageFuture.completeExceptionally(e);
		}
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
		try {
			WampMessage wampMessage = null;
			ByteBuffer duplicate = message.getPayload().duplicate();
			byte[] payloadBytes = new byte[duplicate.remaining()];
			duplicate.get(payloadBytes);

			String acceptedProtocol = session.getAcceptedProtocol();
			if (acceptedProtocol != null) {
				if (WampWebSocketHandler.MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = MessagePackCodec.deserializeWampMessage(payloadBytes, this.msgpackObjectMapper);
				}
				else if (WampWebSocketHandler.SMILE_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = WampMessage.deserialize(this.smileObjectMapper, payloadBytes);
				}
				else if (WampWebSocketHandler.CBOR_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = WampMessage.deserialize(this.cborObjectMapper, payloadBytes);
				}

				if (wampMessage instanceof WelcomeMessage) {
					this.welcomeMessageFuture.complete((WelcomeMessage) wampMessage);
				}
				else {
					this.receivedMessages.add(wampMessage);
					if (this.receivedMessages.size() == this.noOfResults) {
						this.messageFuture.complete(this.receivedMessages);
					}
				}
			}
			else if (this.logger.isErrorEnabled()) {
				this.logger.error("No accepted protocol " + session.getId());
			}
		}
		catch (IOException e) {
			this.welcomeMessageFuture.completeExceptionally(e);
			this.messageFuture.completeExceptionally(e);
		}
	}

	public WampMessage getWampMessage() throws InterruptedException, ExecutionException, TimeoutException {
		List<WampMessage> results = this.messageFuture.get(this.timeout, TimeUnit.SECONDS);
		return results.get(0);
	}

	public List<WampMessage> getWampMessages() throws InterruptedException, ExecutionException, TimeoutException {
		return this.messageFuture.get(this.timeout, TimeUnit.SECONDS);
	}

	public List<WampMessage> getWampMessages(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.messageFuture.get(timeout, unit);
	}

	public WelcomeMessage getWelcomeMessage() throws InterruptedException, ExecutionException, TimeoutException {
		return this.welcomeMessageFuture.get(this.timeout, TimeUnit.SECONDS);
	}

	public void waitForNoMessage() throws InterruptedException, ExecutionException, TimeoutException {
		this.messageFuture.get(this.noMessageTimeoutMillis, TimeUnit.MILLISECONDS);
	}

	public void waitAFewSeconds() {
		try {
			TimeUnit.MILLISECONDS.sleep(this.settleTimeMillis);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
