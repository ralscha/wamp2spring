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
package ch.rasc.wamp2spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import ch.rasc.wamp2spring.message.CallMessage;

/**
 * Annotation that denotes a method that is called when the Dealer receives a
 * {@link CallMessage} and the procedure name matches the configured {@link #value()} or
 * {@link #name()} attribute.
 *
 * If no procedure name is configured the method listens for the name
 * 'beanName.methodName'
 * <p>
 * The method <code>feed</code> in the following example listens for {@link CallMessage}
 * that are sent with the procedure name 'myService.feed'.
 * <p>
 * The method <code>fetchNews</code> is called by the library when a {@link CallMessage}
 * with the procedure name 'fetch.news' arrives.
 * <p>
 *
 * <pre class="code">
 * &#064;Service
 * public class MyService {
 *
 * 	&#064;WampProcedure
 * 	public List<String> feed() {
 * 	}
 *
 * 	&#064;WampProcedure(&quot;fetch.news&quot;)
 * 	public List<String> fetchNews() {
 * 	}
 * }
 * </pre>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WampProcedure {

	/**
	 * Register the annotated method with this procedure name. If empty the default
	 * 'beanName.methodName' is used.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * Register the annotated method with this procedure name. If empty the default
	 * 'beanName.methodName' is used.
	 */
	@AliasFor("value")
	String name() default "";

}
