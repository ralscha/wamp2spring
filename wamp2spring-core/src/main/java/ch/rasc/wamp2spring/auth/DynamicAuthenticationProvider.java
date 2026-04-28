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
package ch.rasc.wamp2spring.auth;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;

/**
 * {@link WampAuthenticationProvider} implementation backed by application-defined
 * challenge and authentication procedures.
 */
public class DynamicAuthenticationProvider implements WampAuthenticationProvider {

	private final String authMethod;

	private final DynamicAuthenticationProcedure authenticationProcedure;

	public DynamicAuthenticationProvider(DynamicAuthenticationProcedure authenticationProcedure) {
		this("dynamic", authenticationProcedure);
	}

	public DynamicAuthenticationProvider(String authMethod, DynamicAuthenticationProcedure authenticationProcedure) {
		this.authMethod = authMethod;
		this.authenticationProcedure = authenticationProcedure;
	}

	@Override
	public String getAuthMethod() {
		return this.authMethod;
	}

	@Override
	public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
		return this.authenticationProcedure.challenge(helloMessage);
	}

	@Override
	public WampAuthentication authenticate(HelloMessage helloMessage, AuthenticateMessage authenticateMessage,
			WampAuthenticationChallenge challenge) {
		WampAuthentication authentication = this.authenticationProcedure.authenticate(helloMessage, authenticateMessage,
				challenge.getState());
		if (authentication == null) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, "Dynamic authentication failed.");
		}
		return authentication;
	}

}