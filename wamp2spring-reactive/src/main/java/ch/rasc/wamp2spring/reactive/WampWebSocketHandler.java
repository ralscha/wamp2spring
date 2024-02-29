/**
 * Copyright the original author or authors.
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
package ch.rasc.wamp2spring.reactive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.InternalCloseMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.util.IdGenerator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class WampWebSocketHandler
		implements WebSocketHandler, ApplicationEventPublisherAware {

	private static final Log logger = LogFactory.getLog(WampWebSocketHandler.class);

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

	private final ConcurrentMap<String, Long> webSocketId2WampSessionId = new ConcurrentHashMap<>();

	private final MessageChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private ApplicationEventPublisher applicationEventPublisher;

	public WampWebSocketHandler(JsonFactory jsonFactory, JsonFactory msgpackFactory,
			JsonFactory cborFactory, JsonFactory smileFactory,
			MessageChannel clientOutboundChannel, MessageChannel clientInboundChannel,
			Features features) {
		this.jsonFactory = jsonFactory;
		this.msgpackFactory = msgpackFactory;
		this.cborFactory = cborFactory;
		this.smileFactory = smileFactory;
		this.clientOutboundChannel = clientOutboundChannel;
		this.clientInboundChannel = clientInboundChannel;

		this.roles = new ArrayList<>();

		if (features.isEnabled(Feature.DEALER)) {
			WampRole dealer = new WampRole(Feature.DEALER.getExternalValue());
			for (Feature feature : features.enabledDealerFeatures()) {
				dealer.addFeature(feature.getExternalValue());
			}
			this.roles.add(dealer);
		}

		if (features.isEnabled(Feature.BROKER)) {
			WampRole broker = new WampRole(Feature.BROKER.getExternalValue());
			for (Feature feature : features.enabledBrokerFeatures()) {
				broker.addFeature(feature.getExternalValue());
			}
			this.roles.add(broker);
		}
	}

	@Override
	public List<String> getSubProtocols() {
		return supportedProtocols;
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		session.receive().doFinally(sig -> {
			Long wampSessionId = this.webSocketId2WampSessionId.get(session.getId());
			if (wampSessionId != null) {
				this.applicationEventPublisher.publishEvent(
						new WampDisconnectEvent(wampSessionId, session.getId(),
								session.getHandshakeInfo().getPrincipal().block()));
				this.webSocketId2WampSessionId.remove(session.getId());
			}
			session.close(); // ?
		}).subscribe(inMsg -> {
			handleIncomingMessage(inMsg, session);
		});

		Publisher<Message<Object>> publisher = MessageChannelReactiveUtils
				.toPublisher(this.clientOutboundChannel);
		return session.send(Flux.from(publisher)
				.filter(msg -> resolveSessionId(msg).equals(session.getId()))
				.map(msg -> handleOutgoingMessage(msg, session)));
	}

	public void handleIncomingMessage(WebSocketMessage inMsg, WebSocketSession session) {

		try {
			WampMessage wampMessage = null;

			if (inMsg.getType() == WebSocketMessage.Type.TEXT) {
				byte[] bytes = new byte[inMsg.getPayload().readableByteCount()];
				inMsg.getPayload().read(bytes);

				wampMessage = WampMessage.deserialize(this.jsonFactory, bytes);
			}
			else if (inMsg.getType() == WebSocketMessage.Type.BINARY) {
				ByteBuffer byteBuffer = inMsg.getPayload().asByteBuffer();

				String acceptedProtocol = session.getHandshakeInfo().getSubProtocol();
				if (acceptedProtocol == null) {
					if (logger.isErrorEnabled()) {
						logger.error(
								"Deserialization failed because no accepted protocol "
										+ inMsg + " in session " + session.getId());
					}
					return;
				}
				if (WampWebSocketHandler.MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = WampMessage.deserialize(this.msgpackFactory,
							byteBuffer.array());
				}
				else if (WampWebSocketHandler.SMILE_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = WampMessage.deserialize(this.smileFactory,
							byteBuffer.array());
				}
				else if (WampWebSocketHandler.CBOR_PROTOCOL.equals(acceptedProtocol)) {
					wampMessage = WampMessage.deserialize(this.cborFactory,
							byteBuffer.array());
				}
			}
			else {
				return;
			}

			if (wampMessage == null) {
				if (logger.isErrorEnabled()) {
					logger.error("Deserialization failed for message " + inMsg
							+ " in session " + session.getId());
				}
				return;
			}

			wampMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
					session.getId());
			wampMessage.setHeader(WampMessageHeader.PRINCIPAL,
					session.getHandshakeInfo().getPrincipal().block());
			wampMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
					this.webSocketId2WampSessionId.get(session.getId()));

			if (wampMessage instanceof HelloMessage) {
				// If this is a helloMessage sent during a running session close the
				// WebSocket connection
				if (wampMessage.getWampSessionId() != null) {
					logger.error("HelloMessage received during running session");
					session.close(CloseStatus.PROTOCOL_ERROR);
				}

				long newWampSessionId = IdGenerator.newRandomId(
						new HashSet<>(this.webSocketId2WampSessionId.values()));
				this.webSocketId2WampSessionId.put(session.getId(), newWampSessionId);

				WelcomeMessage welcomeMessage = new WelcomeMessage(
						(HelloMessage) wampMessage, newWampSessionId, this.roles);
				this.clientOutboundChannel.send(welcomeMessage);

				this.applicationEventPublisher
						.publishEvent(new WampSessionEstablishedEvent(welcomeMessage));
			}
			else if (wampMessage instanceof AbortMessage) {
				session.close(CloseStatus.GOING_AWAY);
			}
			else if (wampMessage instanceof GoodbyeMessage) {
				GoodbyeMessage goodbyeMessage = new GoodbyeMessage(
						WampError.GOODBYE_AND_OUT);
				goodbyeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
						session.getId());
				this.clientOutboundChannel.send(goodbyeMessage);
			}
			else {
				if (wampMessage.getWampSessionId() == null) {
					logger.error("Session not established");
					session.close(CloseStatus.PROTOCOL_ERROR);
				}

				this.clientInboundChannel.send(wampMessage);
			}
		}
		catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error(
						"Failed to parse " + inMsg + " in session " + session.getId(), e);
			}
		}

	}

	private static String resolveSessionId(Message<?> message) {
		return (String) message.getHeaders()
				.get(WampMessageHeader.WEBSOCKET_SESSION_ID.name());
	}

	public WebSocketMessage handleOutgoingMessage(Message<Object> message,
			WebSocketSession session) {
		if (!(message instanceof WampMessage)) {
			logger.error("Expected WampMessage. Ignoring " + message + ".");
			return null;
		}

		if (message instanceof InternalCloseMessage) {
			session.close(CloseStatus.GOING_AWAY);
			return null;
		}

		WampMessage wampMessage = (WampMessage) message;
		JsonFactory useFactory = this.jsonFactory;

		boolean isBinary = false;

		String acceptedProtocol = session.getHandshakeInfo().getSubProtocol();
		if (acceptedProtocol != null) {
			if (WampWebSocketHandler.MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				useFactory = this.msgpackFactory;
			}
			else if (WampWebSocketHandler.SMILE_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				useFactory = this.smileFactory;
			}
			else if (WampWebSocketHandler.CBOR_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				useFactory = this.cborFactory;
			}

			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
					JsonGenerator generator = useFactory.createGenerator(bos)) {
				generator.writeStartArray();
				wampMessage.serialize(generator);
				generator.writeEndArray();
				generator.close();

				if (wampMessage instanceof GoodbyeMessage) {
					InternalCloseMessage cm = new InternalCloseMessage();
					cm.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
					this.clientOutboundChannel.send(cm);
				}

				if (isBinary) {
					return session
							.binaryMessage(factory -> factory.wrap(bos.toByteArray()));
				}
				return session.textMessage(
						new String(bos.toByteArray(), StandardCharsets.UTF_8));

			}
			catch (Throwable ex) {
				// Could be part of normal workflow (e.g. browser tab closed)
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to send WebSocket message to client in session "
							+ session.getId(), ex);
				}

				// Is this an outbound invocation message. In that case we need to feed
				// back an error message
				if (message instanceof InvocationMessage) {
					ErrorMessage errorMessage = new ErrorMessage(
							(InvocationMessage) message, WampError.NETWORK_FAILURE);
					this.clientInboundChannel.send(errorMessage);
				}

				session.close(CloseStatus.PROTOCOL_ERROR);
			}
		}
		else if (logger.isErrorEnabled()) {
			logger.error(
					"Failed to send WebSocket message to client because no accepted protocol "
							+ session.getId());
		}

		return null;
	}

	@Override
	public String toString() {
		return "WampWebSocketHandler " + getSubProtocols();
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
