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

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
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
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.util.IdGenerator;
import ch.rasc.wamp2spring.util.WampUriValidator;

public class WampSessionSupport {

	public static final String WAMP_PRINCIPAL = "wamp2spring.principal";

	public static final String WAMP_AUTH_METHOD = "wamp2spring.auth.method";

	public static final String WAMP_AUTH_PROVIDER = "wamp2spring.auth.provider";

	public static final String WAMP_PENDING_AUTH = "wamp2spring.pending.auth";

	public static final String WAMP_REALM = "wamp2spring.realm";

	private final List<WampRole> roles;

	private final Set<Long> wampSessionIds = ConcurrentHashMap.newKeySet();

	private final Map<String, WampAuthenticationProvider> authenticationProviders;

	private final ApplicationEventPublisher applicationEventPublisher;

	public WampSessionSupport(Features features, List<WampAuthenticationProvider> authenticationProviders,
			ApplicationEventPublisher applicationEventPublisher) {
		this.authenticationProviders = new LinkedHashMap<>();
		for (WampAuthenticationProvider authenticationProvider : authenticationProviders) {
			this.authenticationProviders.putIfAbsent(authenticationProvider.getAuthMethod(), authenticationProvider);
		}
		this.applicationEventPublisher = applicationEventPublisher;
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

	public void populateHeaders(String sessionId, @Nullable Principal transportPrincipal,
			Map<String, Object> attributes, WampMessage wampMessage) {
		wampMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, sessionId);
		wampMessage.setHeader(WampMessageHeader.PRINCIPAL, currentPrincipal(transportPrincipal, attributes));
		wampMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID,
				attributes.get(WampMessageHeader.WAMP_SESSION_ID.name()));
		wampMessage.setHeader(WampMessageHeader.WAMP_REALM, attributes.get(WAMP_REALM));
		wampMessage.setHeader(WampMessageHeader.WAMP_PEER_ROLES,
				attributes.get(WampMessageHeader.WAMP_PEER_ROLES.name()));
		wampMessage.setHeader(WampMessageHeader.AUTH_METHOD, attributes.get(WAMP_AUTH_METHOD));
		wampMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, attributes.get(WAMP_AUTH_PROVIDER));
	}

	public SessionResponse handleHelloMessage(String sessionId, @Nullable Principal transportPrincipal,
			Map<String, Object> attributes, HelloMessage helloMessage) {
		if (attributes.get(WAMP_PENDING_AUTH) != null) {
			return protocolViolation("Received HELLO message while authentication was in progress.");
		}

		if (helloMessage.getWampSessionId() != null) {
			return protocolViolation("Received HELLO message after session was established.");
		}

		try {
			WampUriValidator.validateRealmUri(helloMessage.getRealm());
		}
		catch (WampException ex) {
			return authenticationFailure(
					new WampAuthenticationException(WampError.INVALID_URI, "HELLO realm is not a valid URI."));
		}

		WampAuthenticationProvider authenticationProvider = resolveAuthenticationProvider(helloMessage);
		if (authenticationProvider != null) {
			try {
				WampAuthenticationChallenge challenge = authenticationProvider.challenge(helloMessage);
				attributes.put(WAMP_PENDING_AUTH,
						new PendingAuthentication(helloMessage, authenticationProvider, challenge));
				return new SessionResponse(
						new ChallengeMessage(authenticationProvider.getAuthMethod(), challenge.getExtra()), false);
			}
			catch (WampAuthenticationException ex) {
				return authenticationFailure(ex);
			}
		}

		if (!helloMessage.getAuthMethods().isEmpty()) {
			return authenticationFailure(new WampAuthenticationException(WampError.NO_SUCH_AUTH_METHOD,
					"No requested WAMP auth method is supported."));
		}

		Principal principal = currentPrincipal(transportPrincipal, attributes);
		return establishSession(sessionId, attributes, helloMessage, principal,
				principal == null ? "anonymous" : "transport", principal == null ? "static" : "transport", null);
	}

	public SessionResponse handleAuthenticateMessage(String sessionId, @Nullable Principal transportPrincipal,
			Map<String, Object> attributes, AuthenticateMessage authenticateMessage) {
		PendingAuthentication pendingAuthentication = (PendingAuthentication) attributes.get(WAMP_PENDING_AUTH);
		if (pendingAuthentication == null) {
			return protocolViolation("Received AUTHENTICATE message without an outstanding CHALLENGE.");
		}

		try {
			WampAuthentication authentication = pendingAuthentication.authenticationProvider()
				.authenticate(pendingAuthentication.helloMessage(), authenticateMessage,
						pendingAuthentication.challenge());
			attributes.remove(WAMP_PENDING_AUTH);
			return establishSession(sessionId, attributes, pendingAuthentication.helloMessage(),
					authentication.getPrincipal(), pendingAuthentication.authenticationProvider().getAuthMethod(),
					authentication.getAuthProvider(), authentication.getAuthExtra());
		}
		catch (WampAuthenticationException ex) {
			attributes.remove(WAMP_PENDING_AUTH);
			return authenticationFailure(ex);
		}
	}

	public void afterSessionEnded(String sessionId, @Nullable Principal transportPrincipal,
			Map<String, Object> attributes) {
		Long wampSessionId = (Long) attributes.get(WampMessageHeader.WAMP_SESSION_ID.name());
		if (wampSessionId != null) {
			this.applicationEventPublisher.publishEvent(new WampDisconnectEvent(wampSessionId, sessionId,
					currentPrincipal(transportPrincipal, attributes), (String) attributes.get(WAMP_REALM)));
			this.wampSessionIds.remove(wampSessionId);
			attributes.remove(WampMessageHeader.WAMP_SESSION_ID.name());
			attributes.remove(WAMP_REALM);
		}
		attributes.remove(WAMP_PENDING_AUTH);
	}

	@Nullable public Principal currentPrincipal(@Nullable Principal transportPrincipal, Map<String, Object> attributes) {
		Principal principal = (Principal) attributes.get(WAMP_PRINCIPAL);
		return principal != null ? principal : transportPrincipal;
	}

	private SessionResponse establishSession(String sessionId, Map<String, Object> attributes,
			HelloMessage helloMessage, @Nullable Principal principal, String authMethod, String authProvider,
			@Nullable Map<String, Object> authExtra) {
		long newWampSessionId = IdGenerator.newRandomId(this.wampSessionIds);
		this.wampSessionIds.add(newWampSessionId);
		attributes.put(WampMessageHeader.WAMP_SESSION_ID.name(), newWampSessionId);
		attributes.put(WAMP_REALM, helloMessage.getRealm());
		attributes.put(WampMessageHeader.WAMP_PEER_ROLES.name(), helloMessage.getRoles());
		if (principal != null) {
			attributes.put(WAMP_PRINCIPAL, principal);
		}
		else {
			attributes.remove(WAMP_PRINCIPAL);
		}
		attributes.put(WAMP_AUTH_METHOD, authMethod);
		attributes.put(WAMP_AUTH_PROVIDER, authProvider);

		WelcomeMessage welcomeMessage = new WelcomeMessage(newWampSessionId, this.roles, null, helloMessage.getAuthId(),
				principal != null ? authRole(principal) : null, authMethod, authProvider, authExtra);
		welcomeMessage.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, sessionId);
		welcomeMessage.setHeader(WampMessageHeader.PRINCIPAL, principal);
		welcomeMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, newWampSessionId);
		welcomeMessage.setHeader(WampMessageHeader.WAMP_REALM, helloMessage.getRealm());
		welcomeMessage.setHeader(WampMessageHeader.AUTH_METHOD, authMethod);
		welcomeMessage.setHeader(WampMessageHeader.AUTH_PROVIDER, authProvider);
		this.applicationEventPublisher.publishEvent(new WampSessionEstablishedEvent(welcomeMessage));
		return new SessionResponse(welcomeMessage, false);
	}

	private SessionResponse protocolViolation(String message) {
		return new SessionResponse(new AbortMessage(WampError.PROTOCOL_VIOLATION, message), true);
	}

	private SessionResponse authenticationFailure(WampAuthenticationException ex) {
		return new SessionResponse(new AbortMessage(ex.getError(), ex.getMessage()), true);
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

	@Nullable private static String authRole(Principal principal) {
		WampMessage helper = new AbortMessage(WampError.NETWORK_FAILURE);
		helper.setHeader(WampMessageHeader.PRINCIPAL, principal);
		return helper.getAuthRole();
	}

	public record SessionResponse(WampMessage message, boolean closeTransport) {
	}

	private record PendingAuthentication(HelloMessage helloMessage, WampAuthenticationProvider authenticationProvider,
			WampAuthenticationChallenge challenge) {
	}

}