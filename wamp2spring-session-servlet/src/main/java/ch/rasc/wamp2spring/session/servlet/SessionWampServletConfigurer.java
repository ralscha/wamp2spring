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
package ch.rasc.wamp2spring.session.servlet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.socket.handler.WebSocketConnectHandlerDecoratorFactory;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

import ch.rasc.wamp2spring.servlet.WampServletConfigurer;

public class SessionWampServletConfigurer<S extends Session>
		implements WampServletConfigurer {

	@Autowired
	@SuppressWarnings("rawtypes")
	private SessionRepository sessionRepository;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Bean
	public WebSocketRegistryListener webSocketRegistryListener() {
		return new WebSocketRegistryListener();
	}

	@Bean
	public WebSocketConnectHandlerDecoratorFactory wsConnectHandlerDecoratorFactory() {
		return new WebSocketConnectHandlerDecoratorFactory(this.eventPublisher);
	}

	@Bean
	@SuppressWarnings("unchecked")
	public SessionRepositoryMessageInterceptor<S> sessionRepositoryInterceptor() {
		return new SessionRepositoryMessageInterceptor<>(this.sessionRepository);
	}

	@Override
	public void configureWebSocketHandlerRegistration(
			WebSocketHandlerRegistration registration) {
		registration.addInterceptors(sessionRepositoryInterceptor());
	}

	@Override
	public void configureClientInboundChannel(AbstractMessageChannel channel) {
		channel.addInterceptor(sessionRepositoryInterceptor());
	}

	@Override
	public WebSocketHandler decorateWebSocketHandler(WebSocketHandler webSocketHandler) {
		return wsConnectHandlerDecoratorFactory().decorate(webSocketHandler);
	}
}
