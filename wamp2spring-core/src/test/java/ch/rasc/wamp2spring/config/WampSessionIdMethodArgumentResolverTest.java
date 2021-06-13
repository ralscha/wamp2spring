/**
 * Copyright 2017-2021 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.MethodParameter;

import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.annotation.WampSessionId;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;

public class WampSessionIdMethodArgumentResolverTest {

	private WampSessionIdMethodArgumentResolver resolver;

	private MethodParameter wampSessionIdParameter;

	private MethodParameter stringParameter;

	@Before
	public void setup() throws Exception {
		Method testMethod = getClass().getDeclaredMethod("handleMessage", Long.TYPE,
				String.class);
		this.resolver = new WampSessionIdMethodArgumentResolver();
		this.wampSessionIdParameter = new MethodParameter(testMethod, 0);
		this.stringParameter = new MethodParameter(testMethod, 1);
	}

	@Test
	public void supportsParameterTest() {
		assertThat(this.resolver.supportsParameter(this.wampSessionIdParameter)).isTrue();
		assertThat(this.resolver.supportsParameter(this.stringParameter)).isFalse();
	}

	@Test
	public void resolveArgumentTest() throws Exception {
		CallMessage callMessage = new CallMessage(1, "call");
		callMessage.setHeader(WampMessageHeader.WAMP_SESSION_ID, 23L);
		assertThat(
				this.resolver.resolveArgument(this.wampSessionIdParameter, callMessage))
						.isEqualTo(23L);
	}

	@SuppressWarnings({ "unused" })
	@WampProcedure
	private void handleMessage(@WampSessionId long wampSessionId, String anotherParam) {
		// nothing here
	}

}
