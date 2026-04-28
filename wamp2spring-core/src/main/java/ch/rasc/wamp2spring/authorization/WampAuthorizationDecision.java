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

import ch.rasc.wamp2spring.WampError;

public final class WampAuthorizationDecision {

	private static final WampAuthorizationDecision ALLOW = new WampAuthorizationDecision(true, null);

	private final boolean granted;

	private final WampError error;

	private WampAuthorizationDecision(boolean granted, WampError error) {
		this.granted = granted;
		this.error = error;
	}

	public static WampAuthorizationDecision allow() {
		return ALLOW;
	}

	public static WampAuthorizationDecision deny(WampError error) {
		return new WampAuthorizationDecision(false, error);
	}

	public boolean isGranted() {
		return this.granted;
	}

	public WampError getError() {
		return this.error != null ? this.error : WampError.NOT_AUTHORIZED;
	}

}