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
package ch.rasc.wamp2spring.security.matcher;

import org.springframework.messaging.Message;
import org.springframework.security.messaging.util.matcher.MessageMatcher;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.message.CallMessage;

public class WampCallMessageMatcher implements MessageMatcher<Object> {

	private final DestinationMatch procedureMatch;

	public WampCallMessageMatcher(DestinationMatch procedureMatch) {
		this.procedureMatch = procedureMatch;
	}

	@Override
	public boolean matches(Message<? extends Object> message) {
		if (message instanceof CallMessage) {
			CallMessage callMessage = (CallMessage) message;
			return this.procedureMatch.matches(callMessage.getProcedure());
		}
		return false;
	}

}
