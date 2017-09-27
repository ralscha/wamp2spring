/**
 * Copyright 2017-2017 the original author or authors.
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

package ch.rasc.wamp2spring.event;

import java.security.Principal;

import javax.annotation.Nullable;

import ch.rasc.wamp2spring.message.WampMessage;

/**
 * Base class for the {@link WampProcedureRegisteredEvent} and
 * {@link WampProcedureUnregisteredEvent} event.
 */
public abstract class WampProcedureEvent extends WampEvent {
	private final String procedure;

	private final long registrationId;

	public WampProcedureEvent(WampMessage wampMessage, String procedure,
			long registrationId) {
		super(wampMessage.getWampSessionId(), wampMessage.getWebSocketSessionId(),
				wampMessage.getPrincipal());
		this.procedure = procedure;
		this.registrationId = registrationId;
	}

	public WampProcedureEvent(Long wampSessionId, String webSocketSessionId,
			@Nullable Principal principal, String procedure, long registrationId) {
		super(wampSessionId, webSocketSessionId, principal);
		this.procedure = procedure;
		this.registrationId = registrationId;
	}

	/**
	 * Returns the URI of the procedure to be called.
	 */
	public String getProcedure() {
		return this.procedure;
	}

	/**
	 * Return the registration ID under which the procedure was registered at the Dealer.
	 */
	public long getRegistrationId() {
		return this.registrationId;
	}

}
