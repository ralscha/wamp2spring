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

import java.util.Map;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;

public class TicketWampAuthenticationProvider implements WampAuthenticationProvider {

	private final TicketVerifier ticketVerifier;

	private final String challengeMessage;

	public TicketWampAuthenticationProvider(TicketVerifier ticketVerifier) {
		this(ticketVerifier, "ticket");
	}

	public TicketWampAuthenticationProvider(TicketVerifier ticketVerifier, String challengeMessage) {
		this.ticketVerifier = ticketVerifier;
		this.challengeMessage = challengeMessage;
	}

	@Override
	public String getAuthMethod() {
		return "ticket";
	}

	@Override
	public WampAuthenticationChallenge challenge(HelloMessage helloMessage) {
		String authId = helloMessage.getAuthId();
		if (authId == null || authId.isBlank()) {
			throw new WampAuthenticationException(WampError.INVALID_ARGUMENT,
					"Ticket authentication requires HELLO.Details.authid.");
		}
		return new WampAuthenticationChallenge(Map.of("challenge", this.challengeMessage));
	}

	@Override
	public WampAuthentication authenticate(HelloMessage helloMessage, AuthenticateMessage authenticateMessage,
			WampAuthenticationChallenge challenge) {
		WampAuthentication authentication = this.ticketVerifier.verify(helloMessage.getAuthId(),
				authenticateMessage.getSignature(), helloMessage.getAuthExtra(), authenticateMessage.getExtra());
		if (authentication == null) {
			throw new WampAuthenticationException(WampError.NOT_AUTHORIZED, "Ticket authentication failed.");
		}
		return authentication;
	}

}