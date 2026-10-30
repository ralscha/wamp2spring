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
package ch.rasc.wamp2spring.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.security.matcher.WampRegisterMessageMatcher;
import ch.rasc.wamp2spring.security.matcher.WampSubscribeMessageMatcher;
import ch.rasc.wamp2spring.util.WampUriValidator;

class WampMessageSecurityMetadataSourceRegistryTest {

	@Test
	void emptyConfigurationPermitsMessagesWithoutResolvingAuthentication() {
		var manager = new WampMessageSecurityMetadataSourceRegistry().createAuthorizationManager();
		var decision = manager.authorize(() -> {
			throw new AssertionError("Authentication must not be required for an empty configuration");
		}, new CallMessage(1, "procedure"));
		assertNotNull(decision);
		assertTrue(decision.isGranted());
	}

	@Test
	void unrestrictedTopicMatcherCoversPublishingAndSubscribing() {
		var registry = new WampMessageSecurityMetadataSourceRegistry();
		registry.topicMatchers().denyAll().anyMessage().permitAll();
		assertFalse(granted(registry, PublishMessage.builder(1, "topic").build()));
		assertFalse(granted(registry, new SubscribeMessage(2, "topic")));
		assertTrue(granted(registry, new CallMessage(3, "procedure")));
	}

	@Test
	void unrestrictedProcedureMatcherCoversCallingAndRegistering() {
		var registry = new WampMessageSecurityMetadataSourceRegistry();
		registry.procedureMatchers().denyAll().anyMessage().permitAll();
		assertFalse(granted(registry, new CallMessage(1, "procedure")));
		assertFalse(granted(registry, new RegisterMessage(2, "procedure")));
		assertTrue(granted(registry, new SubscribeMessage(3, "topic")));
	}

	@Test
	void treatsAuthorityNamesAsLiteralValues() {
		String authority = "reader') or permitAll or hasAuthority('other";
		var registry = new WampMessageSecurityMetadataSourceRegistry();
		registry.anyMessage().hasAuthority(authority);
		assertFalse(granted(registry, new CallMessage(1, "procedure"), "other"));
		assertTrue(granted(registry, new CallMessage(1, "procedure"), authority));

		var multiple = new WampMessageSecurityMetadataSourceRegistry();
		multiple.anyMessage().hasAnyAuthority("reader", "director's view");
		assertTrue(granted(multiple, new CallMessage(1, "procedure"), "director's view"));
		assertFalse(granted(multiple, new CallMessage(1, "procedure"), "other"));
	}

	@Test
	void escapesRoleNamesAndValidatesRolePrefixesConsistently() {
		var registry = new WampMessageSecurityMetadataSourceRegistry();
		registry.anyMessage().hasRole("director's");
		assertTrue(granted(registry, new CallMessage(1, "procedure"), "ROLE_director's"));

		var multiple = new WampMessageSecurityMetadataSourceRegistry();
		multiple.anyMessage().hasAnyRole("reader", "director's");
		assertTrue(granted(multiple, new CallMessage(1, "procedure"), "ROLE_director's"));
		assertThrows(IllegalArgumentException.class, () -> multiple.anyMessage().hasAnyRole("ROLE_reader"));
	}

	@Test
	void exactRegistrationPermissionCannotBeExpandedIntoAPattern() {
		var matcher = new WampRegisterMessageMatcher(new DestinationMatch("com.example.read"));
		assertTrue(matcher.matches(new RegisterMessage(1, "com.example.read")));
		assertFalse(matcher.matches(new RegisterMessage(1, "com.example.read", MatchPolicy.PREFIX)));
		assertFalse(matcher.matches(new RegisterMessage(1, "com.example.read", MatchPolicy.WILDCARD)));

		var wildcard = new WampRegisterMessageMatcher(new DestinationMatch("com..read", MatchPolicy.WILDCARD));
		assertTrue(wildcard.matches(new RegisterMessage(1, "com.example.read")));
		assertTrue(wildcard.matches(new RegisterMessage(1, "com..read", MatchPolicy.WILDCARD)));
		assertFalse(wildcard.matches(new RegisterMessage(1, "com.example.read", MatchPolicy.PREFIX)));

		var prefix = new WampRegisterMessageMatcher(new DestinationMatch("com.example.", MatchPolicy.PREFIX));
		assertTrue(prefix.matches(new RegisterMessage(1, "com.example.read", MatchPolicy.PREFIX)));
		assertFalse(prefix.matches(new RegisterMessage(1, "com.", MatchPolicy.PREFIX)));
	}

	@Test
	void prefixRulesDoNotInterpretEmptyComponentsAsWildcards() throws Exception {
		for (String destination : new String[] { "com..read", ".read" }) {
			WampUriValidator.validateProcedureRegistrationUri(destination, MatchPolicy.WILDCARD);
			var rule = new DestinationMatch(destination, MatchPolicy.PREFIX);
			assertFalse(new WampRegisterMessageMatcher(rule)
				.matches(new RegisterMessage(1, destination, MatchPolicy.WILDCARD)));
			assertFalse(new WampSubscribeMessageMatcher(rule)
				.matches(new SubscribeMessage(1, destination, MatchPolicy.WILDCARD)));
		}

		var prefix = new DestinationMatch("com.example.", MatchPolicy.PREFIX);
		assertTrue(new WampRegisterMessageMatcher(prefix)
			.matches(new RegisterMessage(1, "com.example..read", MatchPolicy.WILDCARD)));
		assertTrue(new WampSubscribeMessageMatcher(prefix)
			.matches(new SubscribeMessage(1, "com.example..read", MatchPolicy.WILDCARD)));
	}

	private static boolean granted(WampMessageSecurityMetadataSourceRegistry registry, Message<?> message,
			String... authorities) {
		var authentication = UsernamePasswordAuthenticationToken.authenticated("user", "password",
				AuthorityUtils.createAuthorityList(authorities));
		var decision = registry.createAuthorizationManager().authorize(() -> authentication, message);
		assertNotNull(decision);
		return decision.isGranted();
	}

}
