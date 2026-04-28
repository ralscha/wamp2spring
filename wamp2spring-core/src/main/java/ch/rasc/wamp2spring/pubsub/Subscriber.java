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
package ch.rasc.wamp2spring.pubsub;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

final class Subscriber {

	private final String webSocketSessionId;

	private final long wampSessionId;

	@Nullable private final String authId;

	@Nullable private final String authRole;

	private final boolean subscriptionRevocationSupported;

	Subscriber(String webSocketSessionId, long wampSessionId) {
		this(webSocketSessionId, wampSessionId, null, null, false);
	}

	Subscriber(String webSocketSessionId, long wampSessionId, @Nullable String authId, @Nullable String authRole) {
		this(webSocketSessionId, wampSessionId, authId, authRole, false);
	}

	Subscriber(String webSocketSessionId, long wampSessionId, @Nullable String authId, @Nullable String authRole,
			boolean subscriptionRevocationSupported) {
		this.webSocketSessionId = webSocketSessionId;
		this.wampSessionId = wampSessionId;
		this.authId = authId;
		this.authRole = authRole;
		this.subscriptionRevocationSupported = subscriptionRevocationSupported;
	}

	String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	long getWampSessionId() {
		return this.wampSessionId;
	}

	@Nullable String getAuthId() {
		return this.authId;
	}

	@Nullable String getAuthRole() {
		return this.authRole;
	}

	boolean isSubscriptionRevocationSupported() {
		return this.subscriptionRevocationSupported;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.wampSessionId);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
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
