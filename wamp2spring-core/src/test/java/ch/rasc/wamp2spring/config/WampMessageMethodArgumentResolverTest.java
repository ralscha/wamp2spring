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
package ch.rasc.wamp2spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.message.CallMessage;

public class WampMessageMethodArgumentResolverTest {

	private WampMessageMethodArgumentResolver resolver;

	private MethodParameter messageParameter;

	private MethodParameter stringParameter;

	@BeforeEach
	public void setup() throws Exception {
		Method testMethod = getClass().getDeclaredMethod("handleMessage",
				CallMessage.class, String.class);
		this.resolver = new WampMessageMethodArgumentResolver();
		this.messageParameter = new MethodParameter(testMethod, 0);
		this.stringParameter = new MethodParameter(testMethod, 1);
	}

	@Test
	public void supportsParameterTest() {
		assertThat(this.resolver.supportsParameter(this.messageParameter)).isTrue();
		assertThat(this.resolver.supportsParameter(this.stringParameter)).isFalse();
	}

	@Test
	public void resolveArgumentTest() throws Exception {
		CallMessage callMessage = new CallMessage(1, "call");
		assertThat(this.resolver.resolveArgument(this.messageParameter, callMessage))
				.isEqualTo(callMessage);
	}

	@SuppressWarnings({ "unused" })
	@WampProcedure
	private void handleMessage(CallMessage callMessage, String anotherParam) {
		// nothing here
	}

}
