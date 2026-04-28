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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

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
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.util.IdGenerator;

/**
 * A WebSocket {@link SubProtocolHandler} implementation for the WAMP v2 protocol.
 */
public class WampSubProtocolHandler implements SubProtocolHandler, ApplicationEventPublisherAware {

	private static final Log logger = LogFactory.getLog(WampSubProtocolHandler.class);

	private static final String WAMP_PRINCIPAL = "wamp2spring.principal";

	private static final String WAMP_AUTH_METHOD = "wamp2spring.auth.method";

	private static final String WAMP_AUTH_PROVIDER = "wamp2spring.auth.provider";

	private static final String WAMP_PENDING_AUTH = "wamp2spring.pending.auth";

	private static final String WAMP_REALM = "wamp2spring.realm";

	public static final String JSON_PROTOCOL = "wamp.2.json";

	public static final String MSGPACK_PROTOCOL = "wamp.2.msgpack";

	public static final String CBOR_PROTOCOL = "wamp.2.cbor";

	public static final String SMILE_PROTOCOL = "wamp.2.smile";

	private static final List<String> supportedProtocols = Arrays.asList(MSGPACK_PROTOCOL, JSON_PROTOCOL, CBOR_PROTOCOL,
			SMILE_PROTOCOL);

	private final JsonFactory jsonFactory;

	private final JsonFactory msgpackFactory;

	private final JsonFactory cborFactory;

	private final JsonFactory smileFactory;

	private final List<WampRole> roles;

	private final Set<Long> wampSessionIds = ConcurrentHashMap.newKeySet();

	private final MessageChannel clientInboundChannel;

	private final Map<String, WampAuthenticationProvider> authenticationProviders;

	@Nullable private ApplicationEventPublisher applicationEventPublisher;

	public WampSubProtocolHandler(JsonFactory jsonFactory, JsonFactory msgpackFactory, JsonFactory cborFactory,
			JsonFactory smileFactory, MessageChannel clientInboundChannel, Features features,
			List<WampAuthenticationProvider> authenticationProviders) {
		this.jsonFactory = jsonFactory;
		this.msgpackFactory = msgpackFactory;
		this.cborFactory = cborFactory;
		this.smileFactory = smileFactory;
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
				session.getAttributes().remove(WAMP_PENDING_AUTH);
				session.close(CloseStatus.GOING_AWAY);
			}
			else if (wampMessage instanceof GoodbyeMessage) {
				GoodbyeMessage goodbyeMessage = new GoodbyeMessage(WampError.GOODBYE_AND_OUT);
				handleMessageToClient(session, goodbyeMessage);
				session.close(CloseStatus.GOING_AWAY);
			}
			else {
				if (wampMessage.getWampSessionId() == null) {
					handleProtocolViolation(session,
							session.getAttributes().get(WAMP_PENDING_AUTH) != null
									? "Expected AUTHENTICATE while authentication was in progress."
									: "Received message before session was established.");
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
		if (webSocketMessage instanceof TextMessage textMessage) {
			return WampMessage.deserialize(this.jsonFactory, textMessage.asBytes());
		}
		if (webSocketMessage instanceof BinaryMessage binaryMessage) {
			ByteBuffer duplicate = binaryMessage.getPayload().duplicate();
			byte[] payloadBytes = new byte[duplicate.remaining()];
			duplicate.get(payloadBytes);

			String acceptedProtocol = session.getAcceptedProtocol();
			if (acceptedProtocol == null) {
				if (logger.isErrorEnabled()) {
					logger.error("Deserialization failed because no accepted protocol " + webSocketMessage
							+ " in session " + session.getId());
				}
				return null;
			}
			if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
				return WampMessage.deserialize(this.msgpackFactory, payloadBytes);
			}
			if (SMILE_PROTOCOL.equals(acceptedProtocol)) {
				return WampMessage.deserialize(this.smileFactory, payloadBytes);
			}
			if (CBOR_PROTOCOL.equals(acceptedProtocol)) {
				return WampMessage.deserialize(this.cborFactory, payloadBytes);
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
		wampMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
		wampMessage.setHeader(WampMessageHeader.PRINCIPAL, currentPrincipal(session));
		wampMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
				session.getAttributes().get(WampMessageHeader.WAMP_SESSION_ID.name()));
		wampMessage.setHeader(WampMessageHeader.WAMP_REALM, session.getAttributes().get(WAMP_REALM));
		wampMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES,
				session.getAttributes().get(WampMessageHeader.WAMP_PEER_ROLES.name()));
		wampMessage.setHeader(WampMessageHeader.AUTH_METHOD, session.getAttributes().get(WAMP_AUTH_METHOD));
		wampMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, session.getAttributes().get(WAMP_AUTH_PROVIDER));
	}

	private void handleHelloMessage(WebSocketSession session, HelloMessage helloMessage) throws IOException {
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
				handleMessageToClient(session,
						new ChallengeMessage(authenticationProvider.getAuthMethod(), challenge.getExtra()));
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

	private void handleAuthenticateMessage(WebSocketSession session, AuthenticateMessage authenticateMessage)
			throws IOException {
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
			String authMethod, String authProvider, @Nullable Map<String, Object> authExtra) throws IOException {
		long newWampSessionId = IdGenerator.newRandomId(this.wampSessionIds);
		this.wampSessionIds.add(newWampSessionId);
		session.getAttributes().put(WampMessageHeader.WAMP_SESSION_ID.name(), newWampSessionId);
		session.getAttributes().put(WAMP_REALM, helloMessage.getRealm());
		session.getAttributes().put(WampMessageHeader.WAMP_PEER_ROLES.name(), helloMessage.getRoles());
		if (principal != null) {
			session.getAttributes().put(WAMP_PRINCIPAL, principal);
		}
		else {
			session.getAttributes().remove(WAMP_PRINCIPAL);
		}
		session.getAttributes().put(WAMP_AUTH_METHOD, authMethod);
		session.getAttributes().put(WAMP_AUTH_PROVIDER, authProvider);

		WelcomeMessage welcomeMessage = new WelcomeMessage(newWampSessionId, this.roles, null, helloMessage.getAuthId(),
				principal != null ? authRole(principal) : null, authMethod, authProvider, authExtra);
		welcomeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, session.getId());
		welcomeMessage.setHeader(WampMessageHeader.PRINCIPAL, principal);
		welcomeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, newWampSessionId);
		welcomeMessage.setHeader(WampMessageHeader.WAMP_REALM, helloMessage.getRealm());
		welcomeMessage.setHeader(WampMessageHeader.AUTH_METHOD, authMethod);
		welcomeMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, authProvider);
		handleMessageToClient(session, welcomeMessage);

		getApplicationEventPublisher().publishEvent(new WampSessionEstablishedEvent(welcomeMessage));
	}

	private void handleProtocolViolation(WebSocketSession session, String message) throws IOException {
		logger.error(message);
		handleMessageToClient(session, new AbortMessage(WampError.PROTOCOL_VIOLATION, message));
		session.close(CloseStatus.PROTOCOL_ERROR);
	}

	private void handleAuthenticationFailure(WebSocketSession session, WampAuthenticationException ex)
			throws IOException {
		handleMessageToClient(session, new AbortMessage(ex.getError(), ex.getMessage()));
		session.close(CloseStatus.PROTOCOL_ERROR);
	}

	@Nullable private Principal currentPrincipal(WebSocketSession session) {
		Principal principal = (Principal) session.getAttributes().get(WAMP_PRINCIPAL);
		return principal != null ? principal : session.getPrincipal();
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

	@Override
	public void handleMessageToClient(WebSocketSession session, Message<?> message) {
		if (!(message instanceof WampMessage wampMessage)) {
			logger.error("Expected WampMessage. Ignoring " + message + ".");
			return;
		}
		JsonFactory useFactory = this.jsonFactory;

		boolean isBinary = false;

		String acceptedProtocol = session.getAcceptedProtocol();
		if (acceptedProtocol != null) {
			if (MSGPACK_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				useFactory = this.msgpackFactory;
			}
			else if (SMILE_PROTOCOL.equals(acceptedProtocol)) {
				isBinary = true;
				useFactory = this.smileFactory;
			}
			else if (CBOR_PROTOCOL.equals(acceptedProtocol)) {
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

				if (wampMessage instanceof GoodbyeMessage || wampMessage instanceof AbortMessage) {
					session.close(CloseStatus.GOING_AWAY);
				}
			}
			catch (Throwable ex) {
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
		Long wampSessionId = (Long) session.getAttributes().get(WampMessageHeader.WAMP_SESSION_ID.name());
		if (wampSessionId != null) {
			getApplicationEventPublisher().publishEvent(new WampDisconnectEvent(wampSessionId, session.getId(),
					currentPrincipal(session), (String) session.getAttributes().get(WAMP_REALM)));
			this.wampSessionIds.remove(wampSessionId);
			session.getAttributes().remove(WampMessageHeader.WAMP_SESSION_ID.name());
			session.getAttributes().remove(WAMP_REALM);
		}
		session.getAttributes().remove(WAMP_PENDING_AUTH);
	}

	@Override
	public String toString() {
		return "WampSubProtocolHandler " + getSupportedProtocols();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Nullable private static String authRole(Principal principal) {
		WampMessage helper = new AbortMessage(WampError.NETWORK_FAILURE);
		helper.setHeader(WampMessageHeader.PRINCIPAL, principal);
		return helper.getAuthRole();
	}

	private ApplicationEventPublisher getApplicationEventPublisher() {
		return Objects.requireNonNull(this.applicationEventPublisher);
	}

	private record PendingAuthentication(HelloMessage helloMessage, WampAuthenticationProvider authenticationProvider,
			WampAuthenticationChallenge challenge) {
	}

}