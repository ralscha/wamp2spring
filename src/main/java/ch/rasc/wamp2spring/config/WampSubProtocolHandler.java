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
package ch.rasc.wamp2spring.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SubProtocolHandler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * A WebSocket {@link SubProtocolHandler} for the WAMP v2 protocol.
 */
public class WampSubProtocolHandler
		implements SubProtocolHandler, ApplicationEventPublisherAware {

	private static final Log logger = LogFactory.getLog(WampSubProtocolHandler.class);

	public static final String JSON_PROTOCOL = "wamp.2.json";

	public static final String MSGPACK_PROTOCOL = "wamp.2.msgpack";

	public static final String CBOR_PROTOCOL = "wamp.2.cbor";

	public static final String SMILE_PROTOCOL = "wamp.2.smile";

	private static final List<String> supportedProtocols = Arrays.asList(MSGPACK_PROTOCOL,
			JSON_PROTOCOL, CBOR_PROTOCOL, SMILE_PROTOCOL);

	private final JsonFactory jsonFactory;

	private final JsonFactory msgpackFactory;

	private final JsonFactory cborFactory;

	private final JsonFactory smileFactory;

	private final List<WampRole> roles;

	private final Set<Long> wampSessionIds = ConcurrentHashMap.newKeySet();

	private final MessageChannel clientInboundChannel;

	private ApplicationEventPublisher applicationEventPublisher;

	public WampSubProtocolHandler(JsonFactory jsonFactory, JsonFactory msgpackFactory,
			JsonFactory cborFactory, JsonFactory smileFactory,
			MessageChannel clientInboundChannel) {
		this.jsonFactory = jsonFactory;
		this.msgpackFactory = msgpackFactory;
		this.cborFactory = cborFactory;
		this.smileFactory = smileFactory;
		this.clientInboundChannel = clientInboundChannel;

		this.roles = new ArrayList<>();
		WampRole dealer = new WampRole("dealer");
		dealer.addFeature("caller_identification");
		this.roles.add(dealer);

		WampRole broker = new WampRole("broker");
		broker.addFeature("subscriber_blackwhite_listing");
		broker.addFeature("publisher_exclusion");
		broker.addFeature("publisher_identification");
		broker.addFeature("pattern_based_subscription");
		this.roles.add(broker);
	}

	@Override
	public List<String> getSupportedProtocols() {
		return supportedProtocols;
	}

	/**
	 * Handle incoming WebSocket messages from clients.
	 */
	@Override
	public void handleMessageFromClient(WebSocketSession session,
			WebSocketMessage<?> webSocketMessage, MessageChannel outputChannel) {

		try {
			WampMessage wampMessage = null;

			if (webSocketMessage instanceof TextMessage) {
				wampMessage = WampMessage.deserialize(this.jsonFactory,
						((TextMessage) webSocketMessage).asBytes());
			}
			else if (webSocketMessage instanceof BinaryMessage) {
				ByteBuffer byteBuffer = ((BinaryMessage) webSocketMessage).getPayload();

				String acceptedProtocol = session.getAcceptedProtocol();
				if (acceptedProtocol.equals(WampSubProtocolHandler.MSGPACK_PROTOCOL)) {
					wampMessage = WampMessage.deserialize(this.msgpackFactory,
							byteBuffer.array());
				}
				else if (acceptedProtocol.equals(WampSubProtocolHandler.SMILE_PROTOCOL)) {
					wampMessage = WampMessage.deserialize(this.smileFactory,
							byteBuffer.array());
				}
				else if (acceptedProtocol.equals(WampSubProtocolHandler.CBOR_PROTOCOL)) {
					wampMessage = WampMessage.deserialize(this.cborFactory,
							byteBuffer.array());
				}
			}
			else {
				return;
			}

			if (wampMessage == null) {
				if (logger.isErrorEnabled()) {
					logger.error("Deserialization failed for message " + webSocketMessage
							+ " in session " + session.getId());
				}
				return;
			}

			wampMessage.setWebSocketSession(session);

			if (wampMessage instanceof HelloMessage) {
				// If this is a helloMessage sent during a running session close the
				// websocket connection
				if (wampMessage.getWampSessionId() != null) {
					logger.error("HelloMessage received during running session");
					session.close(CloseStatus.PROTOCOL_ERROR);
				}

				long newWampSessionId = IdGenerator.newRandomId(this.wampSessionIds);
				this.wampSessionIds.add(newWampSessionId);
				session.getAttributes().put(WampMessageHeader.WAMP_SESSION_ID.name(),
						newWampSessionId);

				WelcomeMessage welcomeMessage = new WelcomeMessage(
						(HelloMessage) wampMessage, newWampSessionId, this.roles);
				handleMessageToClient(session, welcomeMessage);

				this.applicationEventPublisher
						.publishEvent(new WampSessionEstablishedEvent(welcomeMessage));
			}
			else if (wampMessage instanceof AbortMessage) {
				session.close(CloseStatus.GOING_AWAY);
			}
			else if (wampMessage instanceof GoodbyeMessage) {
				GoodbyeMessage goodbyeMessage = new GoodbyeMessage(
						WampError.GOODBYE_AND_OUT);
				handleMessageToClient(session, goodbyeMessage);
				session.close(CloseStatus.GOING_AWAY);
			}
			else {
				if (wampMessage.getWampSessionId() == null) {
					logger.error("Session not established");
					session.close(CloseStatus.PROTOCOL_ERROR);
				}

				outputChannel.send(wampMessage);
			}
		}
		catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to parse " + webSocketMessage + " in session "
						+ session.getId(), e);
			}
		}

	}

	/**
	 * Handle WAMP messages going back out to WebSocket clients.
	 */
	@Override
	public void handleMessageToClient(WebSocketSession session, Message<?> message) {
		if (!(message instanceof WampMessage)) {
			logger.error("Expected WampMessage. Ignoring " + message + ".");
			return;
		}

		WampMessage wampMessage = (WampMessage) message;
		JsonFactory useFactory = this.jsonFactory;

		boolean isBinary = false;

		String acceptedProtocol = session.getAcceptedProtocol();
		if (acceptedProtocol.equals(WampSubProtocolHandler.MSGPACK_PROTOCOL)) {
			isBinary = true;
			useFactory = this.msgpackFactory;
		}
		else if (acceptedProtocol.equals(WampSubProtocolHandler.SMILE_PROTOCOL)) {
			isBinary = true;
			useFactory = this.smileFactory;
		}
		else if (acceptedProtocol.equals(WampSubProtocolHandler.CBOR_PROTOCOL)) {
			isBinary = true;
			useFactory = this.cborFactory;
		}

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = useFactory.createGenerator(bos)) {
			generator.writeStartArray();
			wampMessage.serialize(generator);
			generator.writeEndArray();
			generator.close();

			if (isBinary) {
				session.sendMessage(new BinaryMessage(bos.toByteArray()));
			}
			else {
				session.sendMessage(new TextMessage(bos.toByteArray()));
			}

		}
		catch (Throwable ex) {
			// Could be part of normal workflow (e.g. browser tab closed)
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to send WebSocket message to client in session "
						+ session.getId(), ex);
			}

			// Is this an outbound invocation message. In that case we need to feed back
			// an error message
			if (message instanceof InvocationMessage) {
				ErrorMessage errorMessage = new ErrorMessage((InvocationMessage) message,
						WampError.NETWORK_FAILURE);
				this.clientInboundChannel.send(errorMessage);
			}

			try {
				session.close(CloseStatus.PROTOCOL_ERROR);
			}
			catch (IOException e) {
				// Ignore
			}

		}
	}

	@Override
	public String resolveSessionId(Message<?> message) {
		return (String) message.getHeaders()
				.get(WampMessageHeader.WEBSOCKET_SESSION_ID.name());
	}

	@Override
	public void afterSessionStarted(WebSocketSession session,
			MessageChannel outputChannel) {
		// nothing here
	}

	@Override
	public void afterSessionEnded(WebSocketSession session, CloseStatus closeStatus,
			MessageChannel outputChannel) {

		Long wampSessionId = (Long) session.getAttributes()
				.get(WampMessageHeader.WAMP_SESSION_ID.name());
		if (wampSessionId != null) {
			this.applicationEventPublisher.publishEvent(new WampDisconnectEvent(
					wampSessionId, session.getId(), session.getPrincipal()));
			this.wampSessionIds.remove(wampSessionId);
			session.getAttributes().remove(WampMessageHeader.WAMP_SESSION_ID.name());
		}
	}

	@Override
	public String toString() {
		return "WampSubProtocolHandler " + getSupportedProtocols();
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
