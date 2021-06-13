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
package ch.rasc.wamp2spring.event;

import ch.rasc.wamp2spring.message.UnregisterMessage;

/**
 * Fired when a Callee session is removed from a registration.
 */
public class WampProcedureUnregisteredEvent extends WampProcedureEvent {
	public WampProcedureUnregisteredEvent(UnregisterMessage unregisterMessage,
			String procedure, long registrationId) {
		super(unregisterMessage, procedure, registrationId);
	}

	public WampProcedureUnregisteredEvent(WampDisconnectEvent event, String procedure,
			long registrationId) {
		super(event.getWampSessionId(), event.getWebSocketSessionId(),
				event.getPrincipal(), procedure, registrationId);
	}
}
