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
package ch.rasc.wamp2spring.reactive;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import ch.rasc.wamp2spring.config.WampConfiguration;
import ch.rasc.wamp2spring.config.WampConfigurer;

@Configuration
public class WampReactiveConfiguration extends WampConfiguration implements ImportAware {

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		super.setImportMetadata(importMetadata, EnableReactiveWamp.class.getName());
	}

	@Bean
	public WampWebSocketHandler wampWebSocketHandler() {
		return new WampWebSocketHandler(jsonJsonFactory(), msgpackJsonFactory(),
				cborJsonFactory(), smileJsonFactory(), clientOutboundChannel(),
				clientInboundChannel(), this.features);
	}

	@Bean
	public HandlerMapping handlerMapping() {
		Map<String, WebSocketHandler> map = new HashMap<>();

		WampWebSocketHandler wampWebSocketHandler = wampWebSocketHandler();
		WebSocketHandler decoratedWebSocketHandler = decorateWebSocketHandler(
				wampWebSocketHandler);
		for (WampConfigurer wc : this.configurers) {
			if (wc instanceof WampReactiveConfigurer) {
				decoratedWebSocketHandler = ((WampReactiveConfigurer) wc)
						.decorateWebSocketHandler(decoratedWebSocketHandler);
			}
		}
		map.put(getWebSocketHandlerPath(), decoratedWebSocketHandler);

		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setUrlMap(map);
		mapping.setOrder(-1);

		configureHandlerMapping(mapping);
		for (WampConfigurer wc : this.configurers) {
			if (wc instanceof WampReactiveConfigurer) {
				((WampReactiveConfigurer) wc).configureHandlerMapping(mapping);
			}
		}

		return mapping;
	}

	@Bean
	public WebSocketHandlerAdapter handlerAdapter() {
		return new WebSocketHandlerAdapter(webSocketService());
	}

	@Bean
	public WebSocketService webSocketService() {
		return new HandshakeWebSocketService();
	}

	protected void configureHandlerMapping(
			@SuppressWarnings("unused") SimpleUrlHandlerMapping mapping) {
		// nothing here
	}

	protected WebSocketHandler decorateWebSocketHandler(WebSocketHandler handler) {
		return handler;
	}

}
