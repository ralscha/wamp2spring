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

package ch.rasc.wamp2spring.pubsub;

import org.springframework.lang.Nullable;

import ch.rasc.wamp2spring.WampError;

class UnsubscribeResult {

	private final long wampSessionId;

	@Nullable
	private final Subscription subscription;

	private final boolean deleted;

	@Nullable
	private final WampError error;

	public UnsubscribeResult(long wampSessionId, Subscription subscription,
			boolean deleted) {
		this.wampSessionId = wampSessionId;
		this.subscription = subscription;
		this.deleted = deleted;
		this.error = null;
	}

	public UnsubscribeResult(long wampSessionId, WampError error) {
		this.wampSessionId = wampSessionId;
		this.subscription = null;
		this.deleted = false;
		this.error = error;
	}

	public long getWampSessionId() {
		return this.wampSessionId;
	}

	@Nullable
	public Subscription getSubscription() {
		return this.subscription;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	@Nullable
	public WampError getError() {
		return this.error;
	}

}
