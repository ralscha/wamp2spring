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
package ch.rasc.wamp2spring.pubsub;

class Subscriber {
	private final String webSocketSessionId;

	private final long wampSessionId;

	Subscriber(String webSocketSessionId, long wampSessionId) {
		this.webSocketSessionId = webSocketSessionId;
		this.wampSessionId = wampSessionId;
	}

	String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	long getWampSessionId() {
		return this.wampSessionId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.wampSessionId ^ this.wampSessionId >>> 32);
		return result;
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
		Subscriber other = (Subscriber) obj;
		if (this.wampSessionId != other.wampSessionId) {
			return false;
		}
		return true;
	}

}
