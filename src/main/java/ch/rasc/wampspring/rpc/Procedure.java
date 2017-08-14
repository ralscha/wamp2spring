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
package ch.rasc.wampspring.rpc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Procedure {
	private final String procedure;
	private final String webSocketSessionId;
	private final long registrationId;
	private final Set<Long> pendingInvocations;

	public Procedure(String procedure, String webSocketSessionId, long registrationId) {
		this.procedure = procedure;
		this.webSocketSessionId = webSocketSessionId;
		this.registrationId = registrationId;
		this.pendingInvocations = ConcurrentHashMap.newKeySet();
	}

	public String getProcedure() {
		return this.procedure;
	}

	public String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	public void addPendingInvocation(long requestId) {
		this.pendingInvocations.add(requestId);
	}

	public void removePendingInvocation(long requestId) {
		this.pendingInvocations.remove(requestId);
	}

	public Set<Long> getPendingInvocations() {
		return this.pendingInvocations;
	}

	@Override
	public String toString() {
		return "Procedure [procedure=" + this.procedure + ", webSocketSessionId="
				+ this.webSocketSessionId + ", registrationId=" + this.registrationId
				+ ", pendingInvocations=" + this.pendingInvocations + "]";
	}

}
