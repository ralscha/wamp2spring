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
package ch.rasc.wamp2spring.authorization;

import java.security.Principal;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public final class WampAuthorizationContext {

	private final WampAuthorizationAction action;

	private final WampMessage message;

	@Nullable private final String uri;

	private final MatchPolicy matchPolicy;

	public WampAuthorizationContext(WampAuthorizationAction action, WampMessage message, @Nullable String uri,
			MatchPolicy matchPolicy) {
		this.action = action;
		this.message = message;
		this.uri = uri;
		this.matchPolicy = matchPolicy;
	}

	public WampAuthorizationAction getAction() {
		return this.action;
	}

	public WampMessage getMessage() {
		return this.message;
	}

	@Nullable public String getUri() {
		return this.uri;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	@Nullable public Principal getPrincipal() {
		return this.message.getPrincipal();
	}

	@Nullable public String getAuthId() {
		return this.message.getAuthId();
	}

	@Nullable public String getAuthRole() {
		return this.message.getAuthRole();
	}

}