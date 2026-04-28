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
package ch.rasc.wamp2spring.reactive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.auth.WampAuthentication;
import ch.rasc.wamp2spring.auth.WampAuthenticationChallenge;
import ch.rasc.wamp2spring.auth.WampAuthenticationException;
import ch.rasc.wamp2spring.auth.WampAuthenticationProvider;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.event.WampSessionEstablishedEvent;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.ChallengeMessage;
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
import ch.rasc.wamp2spring.util.MessagePackCodec;
import ch.rasc.wamp2spring.util.PooledByteArrayOutputStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

public class WampWebSocketHandler implements WebSocketHandler, ApplicationEventPublisherAware, SmartLifecycle {

	private static final Log logger = LogFactory.getLog(WampWebSocketHandler.class);

	private static final String WAMP_SESSION_ID = "wamp2spring.session.id";

	private static final String WAMP_PRINCIPAL = "wamp2spring.principal";

	private static final String WAMP_AUTH_METHOD = "wamp2spring.auth.method";

	private static final String WAMP_AUTH_PROVIDER = "wamp2spring.auth.provider";

	private static final String WAMP_PENDING_AUTH = "wamp2spring.pending.auth";

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

	private final List<WampRole> roles;

	private final MessageChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private final Map<String, WampAuthenticationProvider> authenticationProviders;

	@Nullable private ApplicationEventPublisher applicationEventPublisher;

	private volatile boolean isRunning;

	private final Set<WebSocketSession> webSocketSessions = ConcurrentHashMap.newKeySet();

	public WampWebSocketHandler(ObjectMapper jsonObjectMapper, ObjectMapper msgpackObjectMapper,
			ObjectMapper cborObjectMapper, ObjectMapper smileObjectMapper, MessageChannel clientOutboundChannel,
			MessageChannel clientInboundChannel, Features features,
			List<WampAuthenticationProvider> authenticationProviders) {
		this.jsonObjectMapper = jsonObjectMapper;
		this.msgpackObjectMapper = msgpackObjectMapper;
		this.cborObjectMapper = cborObjectMapper;
		this.smileObjectMapper = smileObjectMapper;
		this.clientOutboundChannel = clientOutboundChannel;
		this.clientInboundChannel = clientInboundChannel;
		this.authenticationProviders = new LinkedHashMap<>();
		for (WampAuthenticationProvider authenticationProvider : authenticationProviders) {
			this.authenticationProviders.putIfAbsent(authenticationProvider.getAuthMethod(), authenticationProvider);
		}

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
		if (!this.isRunning) {
			return session.close(CloseStatus.GOING_AWAY);
		}

		this.webSocketSessions.add(session);

		return Mono.when(
				session.getHandshakeInfo().getPrincipal().doOnNext(p -> session.getAttributes().put(WAMP_PRINCIPAL, p)),
				session.send(Flux.from(MessageChannelReactiveUtils.toPublisher(this.clientOutboundChannel))
					.filter(msg -> session.getId().equals(resolveSessionId(msg)))
					.<WebSocketMessage>handle((msg, sink) -> {
						WebSocketMessage outgoingMessage = handleOutgoingMessage(msg, session);
						if (outgoingMessage != null) {
							sink.next(outgoingMessage);
						}
					})),
				session.receive().doOnNext(inMsg -> handleIncomingMessage(inMsg, session)))
			.doFinally(sig -> {
				this.webSocketSessions.remove(session);
				session.getAttributes().remove(WAMP_PENDING_AUTH);

				Long wampSessionId = (Long) session.getAttributes().get(WAMP_SESSION_ID);
				Principal principalAttr = currentPrincipal(session);

				if (wampSessionId != null) {
					getApplicationEventPublisher()
						.publishEvent(new WampDisconnectEvent(wampSessionId, session.getId(), principalAttr));
				}
			});
	}

	@Override
	public void start() {
		if (!this.isRunning()) {
			this.isRunning = true;
		}
	}

	@Override
	public void stop() {
		if (this.isRunning()) {
			this.isRunning = false;

			Flux.fromIterable(this.webSocketSessions)
				.flatMap(session -> session.close(CloseStatus.GOING_AWAY))
				.doFinally(sig -> this.webSocketSessions.clear())
				.subscribe();
		}
	}

	@Override
	public boolean isRunning() {
		return this.isRunning;
	}

	private void handleIncomingMessage(WebSocketMessage inMsg, WebSocketSession session) {
		try {
			WampMessage wampMessage = null;

			// Per WAMP spec, the frame type MUST match the negotiated subprotocol:
			// wamp.2.json -> text frames only
			// wamp.2.msgpack -> binary frames only
			// wamp.2.cbor -> binary frames only
			// wamp.2.smile -> binary frames only (wamp2spring-specific)
			String acceptedProtocol = session.getHandshakeInfo().getSubProtocol();
			boolean expectsText = JSON_PROTOCOL.equals(acceptedProtocol);

			if (inMsg.getType() == WebSocketMessage.Type.TEXT) {
				if (acceptedProtocol == null || !expectsText) {
					if (logger.isErrorEnabled()) {
						logger.error("Received text frame on unexpected subprotocol " + acceptedProtocol
								+ " in session " + session.getId());
					}
				}
				else {
					byte[] bytes = new byte[inMsg.getPayload().readableByteCount()];
					inMsg.getPayload().read(bytes);

					wampMessage = WampMessage.deserialize(this.jsonObjectMapper, bytes);
				}
			}
			else if (inMsg.getType() == WebSocketMessage.Type.BINARY) {
				if (acceptedProtocol == null || expectsText) {
					if (logger.isErrorEnabled()) {
						logger.error("Received binary frame on unexpected subprotocol " + acceptedProtocol
								+ " in session " + session.getId());
					}
				}
				else {
					ByteBuffer byteBuffer = inMsg.getPayload().asByteBuffer();
					byte[] bytes = new byte[byteBuffer.remaining()];
					byteBuffer.get(bytes);

					if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
						wampMessage = MessagePackCodec.deserializeWampMessage(bytes, this.msgpackObjectMapper);
					}
					else if (SMILE_PROTOCOL.equals(acceptedProtocol)) {
						wampMessage = WampMessage.deserialize(this.smileObjectMapper, bytes);
					}
					else if (CBOR_PROTOCOL.equals(acceptedProtocol)) {
						wampMessage = WampMessage.deserialize(this.cborObjectMapper, bytes);
					}
				}
			}
			else {
				// Ignore unknown frame types (e.g. PING/PONG handled at transport layer).
				return;
			}

			if (wampMessage == null) {
				if (logger.isErrorEnabled()) {
					logger.error("Deserialization failed for message " + inMsg + " in session " + session.getId());
				}
				AbortMessage deserAbort = new AbortMessage(WampError.PROTOCOL_VIOLATION,
						"Deserialization of incoming message failed.");
				deserAbort.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
				this.clientOutboundChannel.send(deserAbort);
				return;
			}

			Principal principal = currentPrincipal(session);

			wampMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
			wampMessage.setHeader(WampMessageHeader.PRINCIPAL, principal);
			wampMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, session.getAttributes().get(WAMP_SESSION_ID));
			wampMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES,
					session.getAttributes().get(WampMessageHeader.WAMP_PEER_ROLES.name()));
			wampMessage.setHeader(WampMessageHeader.AUTH_METHOD, session.getAttributes().get(WAMP_AUTH_METHOD));
			wampMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, session.getAttributes().get(WAMP_AUTH_PROVIDER));

			if (wampMessage instanceof HelloMessage helloMessage) {
				handleHelloMessage(session, helloMessage);
			}
			else if (wampMessage instanceof AuthenticateMessage authenticateMessage) {
				handleAuthenticateMessage(session, authenticateMessage);
			}
			else if (wampMessage instanceof AbortMessage) {
				session.getAttributes().remove(WAMP_PENDING_AUTH);
				closeSession(session, CloseStatus.GOING_AWAY);
			}
			else if (wampMessage instanceof GoodbyeMessage) {
				GoodbyeMessage goodbyeMessage = new GoodbyeMessage(WampError.GOODBYE_AND_OUT);
				goodbyeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
				this.clientOutboundChannel.send(goodbyeMessage);
			}
			else {
				if (wampMessage.getWampSessionId() == null) {
					handleProtocolViolation(session,
							session.getAttributes().get(WAMP_PENDING_AUTH) != null
									? "Expected AUTHENTICATE while authentication was in progress."
									: "Received message before session was established.");
					return;
				}

				this.clientInboundChannel.send(wampMessage);
			}
		}
		catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to parse " + inMsg + " in session " + session.getId(), e);
			}
		}

	}

	private void handleHelloMessage(WebSocketSession session, HelloMessage helloMessage) {
		if (session.getAttributes().get(WAMP_PENDING_AUTH) != null) {
			handleProtocolViolation(session, "Received HELLO message while authentication was in progress.");
			return;
		}

		if (helloMessage.getWampSessionId() != null) {
			handleProtocolViolation(session, "Received HELLO message after session was established.");
			return;
		}

		WampAuthenticationProvider authenticationProvider = resolveAuthenticationProvider(helloMessage);
		if (authenticationProvider != null) {
			try {
				WampAuthenticationChallenge challenge = authenticationProvider.challenge(helloMessage);
				session.getAttributes()
					.put(WAMP_PENDING_AUTH, new PendingAuthentication(helloMessage, authenticationProvider, challenge));
				ChallengeMessage challengeMessage = new ChallengeMessage(authenticationProvider.getAuthMethod(),
						challenge.getExtra());
				challengeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
				this.clientOutboundChannel.send(challengeMessage);
			}
			catch (WampAuthenticationException ex) {
				handleAuthenticationFailure(session, ex);
			}
			return;
		}

		if (!helloMessage.getAuthMethods().isEmpty()) {
			handleAuthenticationFailure(session, new WampAuthenticationException(WampError.NO_SUCH_AUTH_METHOD,
					"No requested WAMP auth method is supported."));
			return;
		}

		Principal principal = currentPrincipal(session);
		establishSession(session, helloMessage, principal, principal == null ? "anonymous" : "transport",
				principal == null ? "static" : "transport", null);
	}

	private void handleAuthenticateMessage(WebSocketSession session, AuthenticateMessage authenticateMessage) {
		PendingAuthentication pendingAuthentication = (PendingAuthentication) session.getAttributes()
			.get(WAMP_PENDING_AUTH);
		if (pendingAuthentication == null) {
			handleProtocolViolation(session, "Received AUTHENTICATE message without an outstanding CHALLENGE.");
			return;
		}

		try {
			WampAuthentication authentication = pendingAuthentication.authenticationProvider()
				.authenticate(pendingAuthentication.helloMessage(), authenticateMessage,
						pendingAuthentication.challenge());
			session.getAttributes().remove(WAMP_PENDING_AUTH);
			establishSession(session, pendingAuthentication.helloMessage(), authentication.getPrincipal(),
					pendingAuthentication.authenticationProvider().getAuthMethod(), authentication.getAuthProvider(),
					authentication.getAuthExtra());
		}
		catch (WampAuthenticationException ex) {
			session.getAttributes().remove(WAMP_PENDING_AUTH);
			handleAuthenticationFailure(session, ex);
		}
	}

	private void establishSession(WebSocketSession session, HelloMessage helloMessage, @Nullable Principal principal,
			String authMethod, String authProvider, @Nullable Map<String, Object> authExtra) {
		long newWampSessionId = IdGenerator.newRandomId(this.webSocketSessions.stream()
			.map(webSocketSession -> (Long) webSocketSession.getAttributes().get(WAMP_SESSION_ID))
			.filter(Objects::nonNull)
			.collect(Collectors.toSet()));

		session.getAttributes().put(WAMP_SESSION_ID, newWampSessionId);
		session.getAttributes().put(WampMessageHeader.WAMP_PEER_ROLES.name(), helloMessage.getRoles());
		if (principal != null) {
			session.getAttributes().put(WAMP_PRINCIPAL, principal);
		}
		else {
			session.getAttributes().remove(WAMP_PRINCIPAL);
		}
		session.getAttributes().put(WAMP_AUTH_METHOD, authMethod);
		session.getAttributes().put(WAMP_AUTH_PROVIDER, authProvider);

		WelcomeMessage welcomeMessage = new WelcomeMessage(newWampSessionId, this.roles, helloMessage.getAuthId(),
				principal != null ? authRole(principal) : null, authMethod, authProvider, authExtra);
		welcomeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
		welcomeMessage.setHeader(WampMessageHeader.PRINCIPAL, principal);
		welcomeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, newWampSessionId);
		welcomeMessage.setHeader(WampMessageHeader.AUTH_METHOD, authMethod);
		welcomeMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, authProvider);
		this.clientOutboundChannel.send(welcomeMessage);

		getApplicationEventPublisher().publishEvent(new WampSessionEstablishedEvent(welcomeMessage));
	}

	private void handleProtocolViolation(WebSocketSession session, String message) {
		logger.error(message);
		AbortMessage abortMessage = new AbortMessage(WampError.PROTOCOL_VIOLATION, message);
		abortMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
		this.clientOutboundChannel.send(abortMessage);
	}

	private void handleAuthenticationFailure(WebSocketSession session, WampAuthenticationException ex) {
		AbortMessage abortMessage = new AbortMessage(ex.getError(), ex.getMessage());
		abortMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
		this.clientOutboundChannel.send(abortMessage);
	}

	@Nullable private Principal currentPrincipal(WebSocketSession session) {
		return (Principal) session.getAttributes().get(WAMP_PRINCIPAL);
	}

	@Nullable private WampAuthenticationProvider resolveAuthenticationProvider(HelloMessage helloMessage) {
		for (String authMethod : helloMessage.getAuthMethods()) {
			WampAuthenticationProvider authenticationProvider = this.authenticationProviders.get(authMethod);
			if (authenticationProvider != null) {
				return authenticationProvider;
			}
		}
		return null;
	}

	@Nullable private static String resolveSessionId(Message<?> message) {
		return (String) message.getHeaders().get(WampMessageHeader.WEBSOCKET_SESSION_ID.name());
	}

	@Nullable public WebSocketMessage handleOutgoingMessage(Message<Object> message, WebSocketSession session) {
		if (!(message instanceof WampMessage wampMessage)) {
			logger.error("Expected WampMessage. Ignoring " + message + ".");
			return null;
		}

		if (message instanceof InternalCloseMessage) {
			closeSession(session, CloseStatus.GOING_AWAY);
			return null;
		}

		boolean isBinary = false;

		String acceptedProtocol = session.getHandshakeInfo().getSubProtocol();
		ObjectMapper objectMapper = this.jsonObjectMapper;
		if (acceptedProtocol != null) {
			if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				objectMapper = this.msgpackObjectMapper;
			}
			else if (SMILE_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				objectMapper = this.smileObjectMapper;
			}
			else if (CBOR_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				objectMapper = this.cborObjectMapper;
			}

			try {
				byte[] payload = serializeMessage(wampMessage, objectMapper);
				if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
					payload = MessagePackCodec.fromJson(payload, this.msgpackObjectMapper);
				}
				byte[] payloadToSend = payload;

				if (wampMessage instanceof GoodbyeMessage || wampMessage instanceof AbortMessage) {
					InternalCloseMessage cm = new InternalCloseMessage();
					cm.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
					this.clientOutboundChannel.send(cm);
				}

				if (isBinary) {
					return session.binaryMessage(factory -> factory.wrap(payloadToSend));
				}
				return session.textMessage(new String(payloadToSend, StandardCharsets.UTF_8));

			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to send WebSocket message to client in session " + session.getId(), ex);
				}

				if (message instanceof InvocationMessage invocationMessage) {
					ErrorMessage errorMessage = new ErrorMessage(invocationMessage, WampError.NETWORK_FAILURE);
					this.clientInboundChannel.send(errorMessage);
				}

				closeSession(session, CloseStatus.PROTOCOL_ERROR);
			}
		}
		else if (logger.isErrorEnabled()) {
			logger.error("Failed to send WebSocket message to client because no accepted protocol " + session.getId());
		}

		return null;
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

	private static void closeSession(WebSocketSession session, CloseStatus closeStatus) {
		session.close(closeStatus).subscribe();
	}

	@Nullable private static String authRole(Principal principal) {
		WampMessage helper = new AbortMessage(WampError.NETWORK_FAILURE);
		helper.setHeader(WampMessageHeader.PRINCIPAL, principal);
		return helper.getAuthRole();
	}

	@Override
	public String toString() {
		return "WampWebSocketHandler " + getSubProtocols();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	private ApplicationEventPublisher getApplicationEventPublisher() {
		return Objects.requireNonNull(this.applicationEventPublisher);
	}

	private record PendingAuthentication(HelloMessage helloMessage, WampAuthenticationProvider authenticationProvider,
			WampAuthenticationChallenge challenge) {
	}

}
