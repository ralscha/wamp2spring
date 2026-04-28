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
package ch.rasc.wamp2spring.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.auth.WampAuthenticationProvider;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.util.MessagePackCodec;
import ch.rasc.wamp2spring.util.PooledByteArrayOutputStream;

/**
 * A WebSocket {@link SubProtocolHandler} implementation for the WAMP v2 protocol.
 */
public class WampSubProtocolHandler implements SubProtocolHandler, ApplicationEventPublisherAware {

	private static final Log logger = LogFactory.getLog(WampSubProtocolHandler.class);

	public static final String JSON_PROTOCOL = "wamp.2.json";

	public static final String MSGPACK_PROTOCOL = "wamp.2.msgpack";

	public static final String CBOR_PROTOCOL = "wamp.2.cbor";

	public static final String SMILE_PROTOCOL = "wamp.2.smile";

	private static final List<String> supportedProtocols = Arrays.asList(MSGPACK_PROTOCOL, JSON_PROTOCOL, CBOR_PROTOCOL,
			SMILE_PROTOCOL);

	private final ObjectMapper jsonObjectMapper;

	private final ObjectMapper msgpackObjectMapper;

	private final ObjectMapper cborObjectMapper;

	private final ObjectMapper smileObjectMapper;

	private final MessageChannel clientInboundChannel;

	private final Features features;

	private final Map<String, WampAuthenticationProvider> authenticationProviders;

	@Nullable private WampSessionSupport sessionSupport;

	@Nullable private ApplicationEventPublisher applicationEventPublisher;

	public WampSubProtocolHandler(ObjectMapper jsonObjectMapper, ObjectMapper msgpackObjectMapper,
			ObjectMapper cborObjectMapper, ObjectMapper smileObjectMapper, MessageChannel clientInboundChannel,
			Features features, List<WampAuthenticationProvider> authenticationProviders) {
		this.jsonObjectMapper = jsonObjectMapper;
		this.msgpackObjectMapper = msgpackObjectMapper;
		this.cborObjectMapper = cborObjectMapper;
		this.smileObjectMapper = smileObjectMapper;
		this.clientInboundChannel = clientInboundChannel;
		this.features = features;
		this.authenticationProviders = new LinkedHashMap<>();
		for (WampAuthenticationProvider authenticationProvider : authenticationProviders) {
			this.authenticationProviders.putIfAbsent(authenticationProvider.getAuthMethod(), authenticationProvider);
		}
	}

	@Override
	public List<String> getSupportedProtocols() {
		return supportedProtocols;
	}

	@Override
	public void handleMessageFromClient(WebSocketSession session, WebSocketMessage<?> webSocketMessage,
			MessageChannel outputChannel) {

		try {
			WampMessage wampMessage = deserializeIncomingMessage(session, webSocketMessage);
			if (wampMessage == null) {
				handleDeserializationFailure(session, webSocketMessage);
				return;
			}

			populateHeaders(session, wampMessage);

			if (wampMessage instanceof HelloMessage helloMessage) {
				handleHelloMessage(session, helloMessage);
			}
			else if (wampMessage instanceof AuthenticateMessage authenticateMessage) {
				handleAuthenticateMessage(session, authenticateMessage);
			}
			else if (wampMessage instanceof AbortMessage) {
				session.getAttributes().remove(WampSessionSupport.WAMP_PENDING_AUTH);
				session.close(CloseStatus.GOING_AWAY);
			}
			else if (wampMessage instanceof GoodbyeMessage) {
				GoodbyeMessage goodbyeMessage = new GoodbyeMessage(WampError.GOODBYE_AND_OUT);
				handleMessageToClient(session, goodbyeMessage);
				session.close(CloseStatus.GOING_AWAY);
			}
			else {
				if (wampMessage.getWampSessionId() == null) {
					AbortMessage abortMessage = new AbortMessage(WampError.PROTOCOL_VIOLATION,
							session.getAttributes().get(WampSessionSupport.WAMP_PENDING_AUTH) != null
									? "Expected AUTHENTICATE while authentication was in progress."
									: "Received message before session was established.");
					handleMessageToClient(session, abortMessage);
					session.close(CloseStatus.PROTOCOL_ERROR);
					return;
				}
				outputChannel.send(wampMessage);
			}
		}
		catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to parse " + webSocketMessage + " in session " + session.getId(), e);
			}
		}
	}

	@Nullable private WampMessage deserializeIncomingMessage(WebSocketSession session, WebSocketMessage<?> webSocketMessage)
			throws IOException {

		// Per WAMP spec, the frame type MUST match the negotiated subprotocol:
		// wamp.2.json -> text frames only
		// wamp.2.msgpack -> binary frames only
		// wamp.2.cbor -> binary frames only
		// wamp.2.smile -> binary frames only (wamp2spring-specific)
		String acceptedProtocol = session.getAcceptedProtocol();
		if (acceptedProtocol == null) {
			if (logger.isErrorEnabled()) {
				logger.error("Deserialization failed because no accepted protocol " + webSocketMessage + " in session "
						+ session.getId());
			}
			return null;
		}

		boolean expectsText = JSON_PROTOCOL.equals(acceptedProtocol);
		if (webSocketMessage instanceof TextMessage textMessage) {
			if (!expectsText) {
				if (logger.isErrorEnabled()) {
					logger.error("Received text frame on binary subprotocol " + acceptedProtocol + " in session "
							+ session.getId());
				}
				return null;
			}
			return WampMessage.deserialize(this.jsonObjectMapper, textMessage.asBytes());
		}
		if (webSocketMessage instanceof BinaryMessage binaryMessage) {
			if (expectsText) {
				if (logger.isErrorEnabled()) {
					logger.error("Received binary frame on text subprotocol " + acceptedProtocol + " in session "
							+ session.getId());
				}
				return null;
			}

			ByteBuffer duplicate = binaryMessage.getPayload().duplicate();
			byte[] payloadBytes = new byte[duplicate.remaining()];
			duplicate.get(payloadBytes);

			if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
				return MessagePackCodec.deserializeWampMessage(payloadBytes, this.msgpackObjectMapper);
			}
			if (SMILE_PROTOCOL.equals(acceptedProtocol)) {
				return WampMessage.deserialize(this.smileObjectMapper, payloadBytes);
			}
			if (CBOR_PROTOCOL.equals(acceptedProtocol)) {
				return WampMessage.deserialize(this.cborObjectMapper, payloadBytes);
			}
		}
		return null;
	}

	private void handleDeserializationFailure(WebSocketSession session, WebSocketMessage<?> webSocketMessage)
			throws IOException {
		if (logger.isErrorEnabled()) {
			logger.error("Deserialization failed for message " + webSocketMessage + " in session " + session.getId());
		}
		AbortMessage abort = new AbortMessage(WampError.PROTOCOL_VIOLATION,
				"Deserialization of incoming message failed.");
		handleMessageToClient(session, abort);
		session.close(CloseStatus.PROTOCOL_ERROR);
	}

	private void populateHeaders(WebSocketSession session, WampMessage wampMessage) {
		getSessionSupport().populateHeaders(session.getId(), session.getPrincipal(), session.getAttributes(),
				wampMessage);
	}

	private void handleHelloMessage(WebSocketSession session, HelloMessage helloMessage) throws IOException {
		handleMessageToClient(session,
				getSessionSupport()
					.handleHelloMessage(session.getId(), session.getPrincipal(), session.getAttributes(), helloMessage)
					.message());
	}

	private void handleAuthenticateMessage(WebSocketSession session, AuthenticateMessage authenticateMessage)
			throws IOException {
		handleMessageToClient(session,
				getSessionSupport()
					.handleAuthenticateMessage(session.getId(), session.getPrincipal(), session.getAttributes(),
							authenticateMessage)
					.message());
	}

	@Override
	public void handleMessageToClient(WebSocketSession session, Message<?> message) {
		if (!(message instanceof WampMessage wampMessage)) {
			logger.error("Expected WampMessage. Ignoring " + message + ".");
			return;
		}
		boolean isBinary = false;
		ObjectMapper objectMapper = this.jsonObjectMapper;

		String acceptedProtocol = session.getAcceptedProtocol();
		if (acceptedProtocol != null) {
			switch (acceptedProtocol) {
				case MSGPACK_PROTOCOL -> {
					isBinary = true;
					objectMapper = this.msgpackObjectMapper;
				}
				case SMILE_PROTOCOL -> {
					isBinary = true;
					objectMapper = this.smileObjectMapper;
				}
				case CBOR_PROTOCOL -> {
					isBinary = true;
					objectMapper = this.cborObjectMapper;
				}
				default -> {
				}
			}

			try {
				byte[] payload = serializeMessage(wampMessage, objectMapper);
				if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
					payload = MessagePackCodec.fromJson(payload, this.msgpackObjectMapper);
				}

				if (isBinary) {
					session.sendMessage(new BinaryMessage(payload));
				}
				else {
					session.sendMessage(new TextMessage(payload));
				}

				if (wampMessage instanceof GoodbyeMessage || wampMessage instanceof AbortMessage) {
					session.close(CloseStatus.GOING_AWAY);
				}
			}
			catch (Exception | Error ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to send WebSocket message to client in session " + session.getId(), ex);
				}

				if (message instanceof InvocationMessage invocationMessage) {
					ErrorMessage errorMessage = new ErrorMessage(invocationMessage, WampError.NETWORK_FAILURE);
					this.clientInboundChannel.send(errorMessage);
				}

				try {
					session.close(CloseStatus.PROTOCOL_ERROR);
				}
				catch (IOException e) {
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to close WebSocket session " + session.getId(), e);
					}
				}
			}
		}
		else if (logger.isErrorEnabled()) {
			logger.error("Failed to send WebSocket message to client because no accepted protocol " + session.getId());
		}
	}

	private static byte[] serializeMessage(WampMessage wampMessage, ObjectMapper objectMapper) throws IOException {
		// Reuse the calling thread's pooled buffer to avoid a fresh allocation per
		// outbound
		// message. The buffer is always reset on acquire and dropped if it grew unusually
		// large.
		ByteArrayOutputStream bos = PooledByteArrayOutputStream.acquire();
		try (JsonGenerator generator = objectMapper.createGenerator(bos)) {
			generator.writeStartArray();
			wampMessage.serialize(generator);
			generator.writeEndArray();
			generator.close();
			return bos.toByteArray();
		}
		finally {
			PooledByteArrayOutputStream.release(bos);
		}
	}

	@Override
	@Nullable public String resolveSessionId(Message<?> message) {
		return (String) message.getHeaders().get(WampMessageHeader.WEBSOCKET_SESSION_ID.name());
	}

	@Override
	public void afterSessionStarted(WebSocketSession session, MessageChannel outputChannel) {
		// nothing here
	}

	@Override
	public void afterSessionEnded(WebSocketSession session, CloseStatus closeStatus, MessageChannel outputChannel) {
		getSessionSupport().afterSessionEnded(session.getId(), session.getPrincipal(), session.getAttributes());
	}

	@Override
	public String toString() {
		return "WampSubProtocolHandler " + getSupportedProtocols();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	private ApplicationEventPublisher getApplicationEventPublisher() {
		return Objects.requireNonNull(this.applicationEventPublisher);
	}

	private WampSessionSupport getSessionSupport() {
		WampSessionSupport support = this.sessionSupport;
		if (support == null) {
			support = new WampSessionSupport(this.features, this.authenticationProviders.values().stream().toList(),
					getApplicationEventPublisher());
			this.sessionSupport = support;
		}
		return support;
	}

}