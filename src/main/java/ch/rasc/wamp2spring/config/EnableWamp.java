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
package ch.rasc.wamp2spring.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Add this annotation to any {@code @Configuration} class to enable the WAMP v2 support.
 * A default endpoint '/wamp' will be registered.
 *
 * <pre class="code">
 * &#64;SpringBootApplication
 * &#64;EnableWamp
 * public class Application {
 * }
 * </pre>
 *
 * To configure certain aspects of the wamp2spring library you can implement the
 * {@link WampConfigurer} interface with a &#64;Configuration class.
 *
 * <pre class="code">
 * &#64;SpringBootApplication
 * &#64;EnableWamp(disable = Feature.DEALER)
 * public class Application implements WampConfigurer {
 *
 * 	&#64;Override
 * 	public void addArgumentResolvers(
 * 			List<HandlerMethodArgumentResolver> argumentResolvers) {
 * 	}
 * }
 * </pre>
 * <p>
 * If your application needs more control about the wamp2spring configuration you can
 * subclass a &#64;Configuration class from {@link WampConfiguration}. Do not add the
 * &#64;EnableWamp annotation when you configure WAMP this way.
 *
 * <pre class="code">
 * &#64;SpringBootApplication
 * public class Application extends WampConfiguration {
 * 	&#64;Override
 * 	protected String getWebSocketHandlerPath() {
 * 		return "/wampsession";
 * 	}
 * }
 *
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(WampConfiguration.class)
public @interface EnableWamp {
	/**
	 * Disable listed features. By default every supported feature is enabled.
	 */
	Feature[] disable() default {};
}