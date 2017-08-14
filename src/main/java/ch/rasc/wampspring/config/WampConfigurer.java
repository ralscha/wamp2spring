/**
 * Copyright 2017-2017 Ralph Schaer <ralphschaer@gmail.com>
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
package ch.rasc.wampspring.config;

import java.util.List;

import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

@SuppressWarnings("unused")
public interface WampConfigurer {

	/**
	 * Configure the {@link org.springframework.messaging.MessageChannel} used for
	 * incoming messages from WebSocket clients.
	 */
	default void configureClientInboundChannel(AbstractMessageChannel channel) {
		// nothing here
	}

	/**
	 * Add resolvers to support custom controller method argument types.
	 * <p>
	 * This does not override the built-in argument resolvers.
	 * @param argumentResolvers the resolvers to register (initially an empty list)
	 */
	default void addArgumentResolvers(
			List<HandlerMethodArgumentResolver> argumentResolvers) {
		// nothing here
	}

	/*
	 * Configure the WebSocket handler registration for the wamp endpoint
	 */
	default void configureWebSocketHandlerRegistration(
			WebSocketHandlerRegistration registrataion) {
		// nothing here
	}

}
