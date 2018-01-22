/**
 * Copyright 2018-2018 the original author or authors.
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
package ch.rasc.wamp2spring.reactive.config;

import java.util.List;

import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import ch.rasc.wamp2spring.config.Features;

/**
 * Defines methods for configuring WAMP support.
 *
 * <p>
 * Used together with {@link EnableWamp}
 */
@SuppressWarnings("unused")
public interface WampReactiveConfigurer {

	/**
	 * Configures the {@link org.springframework.messaging.MessageChannel} used for
	 * incoming messages from WebSocket clients.
	 */
	default void configureClientInboundChannel(AbstractMessageChannel channel) {
		// nothing here
	}

	/**
	 * Adds resolvers to support custom controller method argument types.
	 * <p>
	 * This does not override the built-in argument resolvers.
	 * @param argumentResolvers the resolvers to register (initially an empty list)
	 */
	default void addArgumentResolvers(
			List<HandlerMethodArgumentResolver> argumentResolvers) {
		// nothing here
	}

	/**
	 * Decorates a WebSocket handler
	 */
	default WebSocketHandler decorateWebSocketHandler(WebSocketHandler webSocketHandler) {
		return webSocketHandler;
	}

	/**
	 * Configures the WebSocket handler registration
	 *
	 * <pre class="code">
	 * &#64;Override
	 * void configureHandlerMapping(SimpleUrlHandlerMapping mapping) {
	 * 	CorsConfiguration corsConfiguration = new CorsConfiguration();
	 * 	corsConfiguration.addAllowedOrigin("http://localhost:8100");
	 * 	mapping.setCorsConfigurations(
	 * 			Collections.singletonMap("/wamp", corsConfiguration));
	 * }
	 * </pre>
	 */
	default void configureHandlerMapping(SimpleUrlHandlerMapping mapping) {
		// nothing here
	}

	/**
	 * Configures wamp2spring features
	 * <p>
	 *
	 * <pre class="code">
	 * &#64;Override
	 * void configureFeatures(Features features) {
	 * 	features.disable(Feature.DEALER);
	 * }
	 * </pre>
	 */
	default void configureFeatures(Features features) {
		// nothing here
	}

}
