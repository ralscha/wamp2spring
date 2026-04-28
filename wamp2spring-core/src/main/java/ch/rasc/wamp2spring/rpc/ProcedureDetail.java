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
package ch.rasc.wamp2spring.rpc;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class ProcedureDetail {

	private final long registrationId;

	private final long created;

	private final String procedure;

	@Nullable private final String realm;

	private final MatchPolicy matchPolicy;

	private final InvocationPolicy invocationPolicy;

	public ProcedureDetail(long registrationId, long created, String procedure, MatchPolicy matchPolicy,
			InvocationPolicy invocationPolicy) {
		this(registrationId, created, null, procedure, matchPolicy, invocationPolicy);
	}

	public ProcedureDetail(long registrationId, long created, @Nullable String realm, String procedure,
			MatchPolicy matchPolicy, InvocationPolicy invocationPolicy) {
		this.registrationId = registrationId;
		this.created = created;
		this.realm = realm;
		this.procedure = procedure;
		this.matchPolicy = matchPolicy;
		this.invocationPolicy = invocationPolicy;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	public long getCreated() {
		return this.created;
	}

	public String getProcedure() {
		return this.procedure;
	}

	@Nullable public String getRealm() {
		return this.realm;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public InvocationPolicy getInvocationPolicy() {
		return this.invocationPolicy;
	}

}