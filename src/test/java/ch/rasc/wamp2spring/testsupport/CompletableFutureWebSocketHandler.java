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
package ch.rasc.wamp2spring.testsupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.fasterxml.jackson.core.JsonFactory;

import ch.rasc.wamp2spring.config.WampSubProtocolHandler;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WelcomeMessage;

public class CompletableFutureWebSocketHandler extends AbstractWebSocketHandler {

	private final CompletableFuture<WelcomeMessage> welcomeMessageFuture;
	private CompletableFuture<List<WampMessage>> messageFuture;

	private final JsonFactory jsonFactory;

	private final JsonFactory msgpackFactory;

	private final JsonFactory cborFactory;

	private final JsonFactory smileFactory;

	private int noOfResults;

	private final int timeout;

	private List<WampMessage> receivedMessages;

	public CompletableFutureWebSocketHandler(JsonFactory jsonFactory,
			JsonFactory msgpackFactory, JsonFactory cborFactory,
			JsonFactory smileFactory) {
		this(1, jsonFactory, msgpackFactory, cborFactory, smileFactory);
	}

	public CompletableFutureWebSocketHandler(int expectedNoOfResults,
			JsonFactory jsonFactory, JsonFactory msgpackFactory, JsonFactory cborFactory,
			JsonFactory smileFactory) {
		this.jsonFactory = jsonFactory;
		this.msgpackFactory = msgpackFactory;
		this.cborFactory = cborFactory;
		this.smileFactory = smileFactory;
		this.timeout = getTimeoutValue();
		this.welcomeMessageFuture = new CompletableFuture<>();
		this.reset(expectedNoOfResults);
	}

	private static int getTimeoutValue() {
		int timeout = 2;
		try {
			String timeoutValue = System.getenv("WS_TIMEOUT");
			timeout = Integer.parseInt(timeoutValue);
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
	protected void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {

		try {
			WampMessage wampMessage = WampMessage.deserialize(this.jsonFactory,
					message.asBytes());

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
		catch (IOException e) {
			this.welcomeMessageFuture.completeExceptionally(e);
			this.messageFuture.completeExceptionally(e);
		}
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
			throws Exception {
		try {
			WampMessage wampMessage = null;

			String acceptedProtocol = session.getAcceptedProtocol();
			if (acceptedProtocol.equals(WampSubProtocolHandler.MSGPACK_PROTOCOL)) {
				wampMessage = WampMessage.deserialize(this.msgpackFactory,
						message.getPayload().array());
			}
			else if (acceptedProtocol.equals(WampSubProtocolHandler.SMILE_PROTOCOL)) {
				wampMessage = WampMessage.deserialize(this.smileFactory,
						message.getPayload().array());
			}
			else if (acceptedProtocol.equals(WampSubProtocolHandler.CBOR_PROTOCOL)) {
				wampMessage = WampMessage.deserialize(this.cborFactory,
						message.getPayload().array());
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
		catch (IOException e) {
			this.welcomeMessageFuture.completeExceptionally(e);
			this.messageFuture.completeExceptionally(e);
		}
	}

	public WampMessage getWampMessage()
			throws InterruptedException, ExecutionException, TimeoutException {
		List<WampMessage> results = this.messageFuture.get(this.timeout,
				TimeUnit.SECONDS);
		return results.get(0);
	}

	public List<WampMessage> getWampMessages()
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.messageFuture.get(this.timeout, TimeUnit.SECONDS);
	}

	public WelcomeMessage getWelcomeMessage()
			throws InterruptedException, ExecutionException, TimeoutException {
		return this.welcomeMessageFuture.get(this.timeout, TimeUnit.SECONDS);
	}
}
