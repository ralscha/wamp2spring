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
package ch.rasc.wamp2spring.servlet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;

import ch.rasc.wamp2spring.config.WampConfiguration;
import ch.rasc.wamp2spring.config.WampConfigurer;

@Configuration
public class WampServletConfiguration extends WampConfiguration implements ImportAware {

	@Nullable
	private ServletWebSocketHandlerRegistry handlerRegistry;

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		super.setImportMetadata(importMetadata, EnableServletWamp.class.getName());
	}

	@Bean
	public SubProtocolWebSocketHandler subProtocolWebSocketHandler() {
		SubProtocolWebSocketHandler subProtocolWebSocketHandler = new SubProtocolWebSocketHandler(
				clientInboundChannel(), clientOutboundChannel());
		subProtocolWebSocketHandler.addProtocolHandler(wampSubProtocolHandler());
		return subProtocolWebSocketHandler;
	}

	@Bean
	public WampSubProtocolHandler wampSubProtocolHandler() {
		return new WampSubProtocolHandler(jsonJsonFactory(), msgpackJsonFactory(),
				cborJsonFactory(), smileJsonFactory(), clientInboundChannel(),
				this.features);
	}

	@Bean
	public HandlerMapping webSocketHandlerMapping() {
		ServletWebSocketHandlerRegistry registry = initHandlerRegistry();
		return registry.getHandlerMapping();
	}

	private ServletWebSocketHandlerRegistry initHandlerRegistry() {
		if (this.handlerRegistry == null) {
			this.handlerRegistry = new ServletWebSocketHandlerRegistry();
			registerWebSocketHandlers(this.handlerRegistry);
		}
		return this.handlerRegistry;
	}

	protected void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		SubProtocolWebSocketHandler subProtocolWebSocketHandler = subProtocolWebSocketHandler();

		Integer sendTimeLimit = getSendTimeLimit();
		if (sendTimeLimit != null) {
			subProtocolWebSocketHandler.setSendTimeLimit(sendTimeLimit);
		}

		Integer sendBufferSizeLimit = getSendBufferSizeLimit();
		if (sendBufferSizeLimit != null) {
			subProtocolWebSocketHandler.setSendBufferSizeLimit(sendBufferSizeLimit);
		}

		WebSocketHandler decoratedHandler = subProtocolWebSocketHandler;
		decoratedHandler = decorateWebSocketHandler(decoratedHandler);
		for (WampConfigurer wc : this.configurers) {
			if (wc instanceof WampServletConfigurer) {
				decoratedHandler = ((WampServletConfigurer) wc)
						.decorateWebSocketHandler(decoratedHandler);
			}
		}

		WebSocketHandlerRegistration registration = registry.addHandler(decoratedHandler,
				getWebSocketHandlerPath());

		registration.setHandshakeHandler(getHandshakeHandler());

		configureWebSocketHandlerRegistration(registration);
		for (WampConfigurer wc : this.configurers) {
			if (wc instanceof WampServletConfigurer) {
				((WampServletConfigurer) wc)
						.configureWebSocketHandlerRegistration(registration);
			}
		}
	}

	protected WebSocketHandler decorateWebSocketHandler(WebSocketHandler handler) {
		return handler;
	}

	protected void configureWebSocketHandlerRegistration(
			@SuppressWarnings("unused") WebSocketHandlerRegistration registration) {
		// nothing here
	}

	protected HandshakeHandler getHandshakeHandler() {
		return new PreferBinaryHandshakeHandler();
	}

	@Nullable
	protected Integer getSendTimeLimit() {
		return null;
	}

	@Nullable
	protected Integer getSendBufferSizeLimit() {
		return null;
	}

}
