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
package ch.rasc.wamp2spring.security.reactive;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.messaging.access.expression.DefaultMessageSecurityExpressionHandler;
import org.springframework.security.messaging.access.expression.MessageExpressionVoter;
import org.springframework.security.messaging.access.intercept.ChannelSecurityInterceptor;
import org.springframework.security.messaging.access.intercept.MessageSecurityMetadataSource;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;

import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.reactive.WampReactiveConfigurer;
import ch.rasc.wamp2spring.security.WampMessageSecurityMetadataSourceRegistry;

public abstract class AbstractSecurityWampReactiveConfigurer
		implements WampReactiveConfigurer {

	private SecurityExpressionHandler<Message<Object>> defaultExpressionHandler = new DefaultMessageSecurityExpressionHandler<>();

	@Nullable
	private SecurityExpressionHandler<Message<Object>> expressionHandler;

	private final WampMessageSecurityMetadataSourceRegistry inboundRegistry = new WampMessageSecurityMetadataSourceRegistry();

	@Override
	public void addArgumentResolvers(
			List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
	}

	@Override
	public void configureClientInboundChannel(AbstractMessageChannel channel) {
		ChannelSecurityInterceptor inboundChannelSecurity = inboundChannelSecurity();
		channel.addInterceptor(securityContextChannelInterceptor());
		if (this.inboundRegistry.containsMapping()) {
			channel.addInterceptor(inboundChannelSecurity);
		}
	}

	@Bean
	public ChannelSecurityInterceptor inboundChannelSecurity() {
		ChannelSecurityInterceptor channelSecurityInterceptor = new ChannelSecurityInterceptor(
				inboundMessageSecurityMetadataSource());
		MessageExpressionVoter<Object> voter = new MessageExpressionVoter<>();
		voter.setExpressionHandler(getMessageExpressionHandler());

		List<AccessDecisionVoter<? extends Object>> voters = new ArrayList<>();
		voters.add(voter);

		AffirmativeBased manager = new AffirmativeBased(voters);
		channelSecurityInterceptor.setAccessDecisionManager(manager);
		return channelSecurityInterceptor;
	}

	@Bean
	public SecurityContextChannelInterceptor securityContextChannelInterceptor() {
		return new SecurityContextChannelInterceptor(WampMessageHeader.PRINCIPAL.name());
	}

	@Bean
	public MessageSecurityMetadataSource inboundMessageSecurityMetadataSource() {
		this.inboundRegistry.expressionHandler(getMessageExpressionHandler());
		configureInbound(this.inboundRegistry);
		return this.inboundRegistry.createMetadataSource();
	}

	protected void configureInbound(
			@SuppressWarnings("unused") WampMessageSecurityMetadataSourceRegistry messages) {
		// by default nothing here
	}

	@Autowired(required = false)
	public void setMessageExpressionHandler(
			List<SecurityExpressionHandler<Message<Object>>> expressionHandlers) {
		if (expressionHandlers.size() == 1) {
			this.expressionHandler = expressionHandlers.get(0);
		}
	}

	@Autowired(required = false)
	public void setObjectPostProcessor(ObjectPostProcessor<Object> objectPostProcessor) {
		this.defaultExpressionHandler = objectPostProcessor
				.postProcess(this.defaultExpressionHandler);
	}

	private SecurityExpressionHandler<Message<Object>> getMessageExpressionHandler() {
		if (this.expressionHandler == null) {
			return this.defaultExpressionHandler;
		}
		return this.expressionHandler;
	}
}
