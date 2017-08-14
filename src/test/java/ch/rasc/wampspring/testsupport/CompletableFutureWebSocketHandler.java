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

import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.message.WelcomeMessage;

public class CompletableFutureWebSocketHandler extends AbstractWebSocketHandler {

	private final CompletableFuture<WelcomeMessage> welcomeMessageFuture;
	private CompletableFuture<List<WampMessage>> messageFuture;

	private final JsonFactory jsonFactory;

	protected final JsonFactory msgpackFactory;

	private int noOfResults;

	private final int timeout;

	private List<WampMessage> receivedMessages;

	public CompletableFutureWebSocketHandler(JsonFactory jsonFactory,
			JsonFactory msgpackFactory) {
		this(1, jsonFactory, msgpackFactory);
	}

	public CompletableFutureWebSocketHandler(int expectedNoOfResults,
			JsonFactory jsonFactory, JsonFactory msgpackFactory) {
		this.jsonFactory = jsonFactory;
		this.msgpackFactory = msgpackFactory;
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
			WampMessage wampMessage = WampMessage.deserialize(this.msgpackFactory,
					message.getPayload().array());
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
