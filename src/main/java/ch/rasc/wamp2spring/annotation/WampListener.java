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
package ch.rasc.wamp2spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

/**
 * Annotation that denotes a method that is called when the server receives a PUBLISH
 * message and the topic matches one of the listed values of the annotation (
 * {@link #topic()}).
 *
 * If no topic is provided the method listens for the topic 'beanName.methodName'
 * <p>
 * The method <code>feed</code> in the following example listens for PUBLISH messages that
 * are sent to the topic 'myService.feed'. <br>
 * The method <code>publishNews</code> is called by the library when a PUBLISH message
 * with the topic 'topic.news' arrives.
 *
 * <pre class="code">
 * &#064;Service
 * public class MyService {
 *
 * 	&#064;WampListener
 * 	public void feed() {
 * 	}
 *
 * 	&#064;WampListener(&quot;topic.news&quot;)
 * 	public void publishNews(String news) {
 * 	}
 * }
 * </pre>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WampListener {

	/**
	 * One or more topics the method should listen on. If empty the default value
	 * 'beanName.methodName' is used.
	 */
	@AliasFor("topic")
	String[] value() default {};

	/**
	 * One or more topics the method should listen on. If empty the default value
	 * 'beanName.methodName' is used.
	 */
	@AliasFor("value")
	String[] topic() default {};

	/**
	 * Specifies the match policy
	 */
	MatchPolicy match() default MatchPolicy.EXACT;

}
