/**
 * Copyright 2017-2018 the original author or authors.
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
package ch.rasc.wamp2spring.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.messaging.access.expression.DefaultMessageSecurityExpressionHandler;
import org.springframework.security.messaging.access.expression.ExpressionBasedMessageSecurityMetadataSourceFactory;
import org.springframework.security.messaging.access.intercept.MessageSecurityMetadataSource;
import org.springframework.security.messaging.util.matcher.MessageMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.security.matcher.WampCallMessageMatcher;
import ch.rasc.wamp2spring.security.matcher.WampMessageMatcher;
import ch.rasc.wamp2spring.security.matcher.WampPublishMessageMatcher;
import ch.rasc.wamp2spring.security.matcher.WampRegisterMessageMatcher;
import ch.rasc.wamp2spring.security.matcher.WampSubscribeMessageMatcher;

public class WampMessageSecurityMetadataSourceRegistry {

	private SecurityExpressionHandler<Message<Object>> expressionHandler = new DefaultMessageSecurityExpressionHandler<>();

	final LinkedHashMap<MessageMatcher<?>, String> matcherToExpression = new LinkedHashMap<>();

	public Constraint anyMessage() {
		return matchers(MessageMatcher.ANY_MESSAGE);
	}

	public Constraint registerMessage() {
		return matchers(new WampMessageMatcher(RegisterMessage.CODE));
	}

	public Constraint callMessage() {
		return matchers(new WampMessageMatcher(CallMessage.CODE));
	}

	public Constraint subscribeMessage() {
		return matchers(new WampMessageMatcher(SubscribeMessage.CODE));
	}

	public Constraint publishMessage() {
		return matchers(new WampMessageMatcher(PublishMessage.CODE));
	}

	public Constraint registerMessage(DestinationMatch... destinationMatches) {
		if (destinationMatches != null && destinationMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : destinationMatches) {
				messageMatchers.add(new WampRegisterMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return registerMessage();
	}

	public Constraint registerMessage(String... procedures) {
		if (procedures != null && procedures.length > 0) {
			List<DestinationMatch> destionationMatches = new ArrayList<>();
			for (String procedure : procedures) {
				destionationMatches
						.add(new DestinationMatch(procedure, MatchPolicy.EXACT));
			}
			return registerMessage(destionationMatches
					.toArray(new DestinationMatch[destionationMatches.size()]));
		}
		return registerMessage();
	}

	public Constraint callMessage(DestinationMatch... destinationMatches) {
		if (destinationMatches != null && destinationMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : destinationMatches) {
				messageMatchers.add(new WampCallMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return callMessage();
	}

	public Constraint callMessage(String... procedures) {
		if (procedures != null && procedures.length > 0) {
			List<DestinationMatch> destionationMatches = new ArrayList<>();
			for (String procedure : procedures) {
				destionationMatches
						.add(new DestinationMatch(procedure, MatchPolicy.EXACT));
			}
			return callMessage(destionationMatches
					.toArray(new DestinationMatch[destionationMatches.size()]));
		}
		return callMessage();
	}

	public Constraint subscribeMessage(DestinationMatch... destinationMatches) {
		if (destinationMatches != null && destinationMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : destinationMatches) {
				messageMatchers.add(new WampSubscribeMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return subscribeMessage();
	}

	public Constraint subscribeMessage(String... topics) {
		if (topics != null && topics.length > 0) {
			List<DestinationMatch> destionationMatches = new ArrayList<>();
			for (String topic : topics) {
				destionationMatches.add(new DestinationMatch(topic, MatchPolicy.EXACT));
			}
			return subscribeMessage(destionationMatches
					.toArray(new DestinationMatch[destionationMatches.size()]));
		}
		return subscribeMessage();
	}

	public Constraint publishMessage(DestinationMatch... destinationMatches) {
		if (destinationMatches != null && destinationMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : destinationMatches) {
				messageMatchers.add(new WampPublishMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return publishMessage();
	}

	public Constraint publishMessage(String... topics) {
		if (topics != null && topics.length > 0) {
			List<DestinationMatch> destionationMatches = new ArrayList<>();
			for (String topic : topics) {
				destionationMatches.add(new DestinationMatch(topic, MatchPolicy.EXACT));
			}
			return publishMessage(destionationMatches
					.toArray(new DestinationMatch[destionationMatches.size()]));
		}
		return publishMessage();
	}

	public Constraint topicMatchers(DestinationMatch... topicMatches) {
		if (topicMatches != null && topicMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : topicMatches) {
				messageMatchers.add(new WampSubscribeMessageMatcher(destinationMatch));
				messageMatchers.add(new WampPublishMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return publishMessage();
	}

	public Constraint procedureMatchers(DestinationMatch... procedureMatches) {
		if (procedureMatches != null && procedureMatches.length > 0) {
			List<MessageMatcher<?>> messageMatchers = new ArrayList<>();
			for (DestinationMatch destinationMatch : procedureMatches) {
				messageMatchers.add(new WampRegisterMessageMatcher(destinationMatch));
				messageMatchers.add(new WampCallMessageMatcher(destinationMatch));
			}
			return new Constraint(messageMatchers);
		}
		return callMessage();
	}

	public Constraint matchers(MessageMatcher<?>... matchers) {
		return new Constraint(Arrays.asList(matchers));
	}

	public void expressionHandler(
			SecurityExpressionHandler<Message<Object>> exprHandler) {
		this.expressionHandler = exprHandler;
	}

	protected MessageSecurityMetadataSource createMetadataSource() {
		return ExpressionBasedMessageSecurityMetadataSourceFactory
				.createExpressionMessageMetadataSource(this.matcherToExpression,
						this.expressionHandler);
	}

	protected boolean containsMapping() {
		return !this.matcherToExpression.isEmpty();
	}

	public class Constraint {
		private final List<MessageMatcher<?>> messageMatchers;

		Constraint(List<MessageMatcher<?>> messageMatchers) {
			this.messageMatchers = messageMatchers;
		}

		public WampMessageSecurityMetadataSourceRegistry hasRole(String role) {
			return access(hasRoleBuilder(role));
		}

		public WampMessageSecurityMetadataSourceRegistry hasAnyRole(String... roles) {
			return access(hasAnyRoleBuilder(roles));
		}

		public WampMessageSecurityMetadataSourceRegistry hasAuthority(String authority) {
			return access(hasAuthorityBuilder(authority));
		}

		public WampMessageSecurityMetadataSourceRegistry hasAnyAuthority(
				String... authorities) {
			return access(hasAnyAuthorityBuilder(authorities));
		}

		public WampMessageSecurityMetadataSourceRegistry permitAll() {
			return access("permitAll");
		}

		public WampMessageSecurityMetadataSourceRegistry anonymous() {
			return access("anonymous");
		}

		public WampMessageSecurityMetadataSourceRegistry rememberMe() {
			return access("rememberMe");
		}

		public WampMessageSecurityMetadataSourceRegistry denyAll() {
			return access("denyAll");
		}

		public WampMessageSecurityMetadataSourceRegistry authenticated() {
			return access("authenticated");
		}

		public WampMessageSecurityMetadataSourceRegistry fullyAuthenticated() {
			return access("fullyAuthenticated");
		}

		public WampMessageSecurityMetadataSourceRegistry access(String attribute) {
			for (MessageMatcher<?> messageMatcher : this.messageMatchers) {
				WampMessageSecurityMetadataSourceRegistry.this.matcherToExpression
						.put(messageMatcher, attribute);
			}
			return WampMessageSecurityMetadataSourceRegistry.this;
		}

		private String hasAnyRoleBuilder(String... authorities) {
			String anyAuthorities = StringUtils.arrayToDelimitedString(authorities,
					"','ROLE_");
			return "hasAnyRole('ROLE_" + anyAuthorities + "')";
		}

		private String hasRoleBuilder(String role) {
			Assert.notNull(role, "role cannot be null");
			if (role.startsWith("ROLE_")) {
				throw new IllegalArgumentException(
						"role should not start with 'ROLE_' since it is automatically inserted. Got '"
								+ role + "'");
			}
			return "hasRole('ROLE_" + role + "')";
		}

		private String hasAuthorityBuilder(String authority) {
			return "hasAuthority('" + authority + "')";
		}

		private String hasAnyAuthorityBuilder(String... authorities) {
			String anyAuthorities = StringUtils.arrayToDelimitedString(authorities,
					"','");
			return "hasAnyAuthority('" + anyAuthorities + "')";
		}
	}
}
