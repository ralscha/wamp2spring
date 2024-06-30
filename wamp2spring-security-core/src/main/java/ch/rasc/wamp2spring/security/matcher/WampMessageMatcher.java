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

import java.util.Objects;

import org.springframework.messaging.Message;
import org.springframework.security.messaging.util.matcher.MessageMatcher;

import ch.rasc.wamp2spring.message.WampMessage;

/**
 * A {@link MessageMatcher} that matches if the provided {@link Message} has the same code
 * as the value specified in the constructor
 */
public class WampMessageMatcher implements MessageMatcher<Object> {
	private final int code;

	public WampMessageMatcher(int code) {
		this.code = code;
	}

	@Override
	public boolean matches(Message<? extends Object> message) {
		if (message instanceof WampMessage) {
			return ((WampMessage) message).getCode() == this.code;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.code);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WampMessageMatcher other = (WampMessageMatcher) obj;
		if (this.code != other.code) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "WampMessageMatcher [code=" + this.code + "]";
	}

}