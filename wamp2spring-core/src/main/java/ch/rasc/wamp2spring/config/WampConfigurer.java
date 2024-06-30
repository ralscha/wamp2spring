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
package ch.rasc.wamp2spring.config;

import java.util.List;

import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.AbstractMessageChannel;

/**
 * Defines methods for configuring WAMP support.
 *
 * <p>
 * Used together with EnableServletWamp or EnableReactiveWamp
 */
public interface WampConfigurer {
	/**
	 * Configures the {@link org.springframework.messaging.MessageChannel} used for
	 * incoming messages from WebSocket clients.
	 */
	default void configureClientInboundChannel(
			@SuppressWarnings("unused") AbstractMessageChannel channel) {
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
	default void configureFeatures(@SuppressWarnings("unused") Features features) {
		// nothing here
	}

}
