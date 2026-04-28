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

import java.util.List;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.message.ErrorMessage;

class UnregisterResult {

	private final boolean success;

	@Nullable private final String procedure;

	private final long registrationId;

	private final boolean deleted;

	@Nullable private final Procedure procedureObject;

	@Nullable private final List<ErrorMessage> invocationErrors;

	UnregisterResult(boolean success, @Nullable Procedure proc, boolean deleted) {
		this(success, proc, deleted, null);
	}

	UnregisterResult(boolean success, @Nullable Procedure proc, boolean deleted,
			@Nullable List<ErrorMessage> invocationErrors) {
		this.success = success;
		this.deleted = deleted;
		this.procedureObject = proc;
		if (proc != null) {
			this.procedure = proc.getProcedure();
			this.registrationId = proc.getRegistrationId();
		}
		else {
			this.procedure = null;
			this.registrationId = -1;
		}
		this.invocationErrors = invocationErrors;
	}

	boolean isSuccess() {
		return this.success;
	}

	@Nullable String getProcedure() {
		return this.procedure;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	boolean isDeleted() {
		return this.deleted;
	}

	@Nullable List<ErrorMessage> getInvocationErrors() {
		return this.invocationErrors;
	}

	@Nullable Procedure getProcedureObject() {
		return this.procedureObject;
	}

}
