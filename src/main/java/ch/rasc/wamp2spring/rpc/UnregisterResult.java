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
package ch.rasc.wamp2spring.rpc;

import java.util.List;

import javax.annotation.Nullable;

import ch.rasc.wamp2spring.message.ErrorMessage;

class UnregisterResult {
	private final boolean success;

	private final String procedure;

	private final long registrationId;

	@Nullable
	private final List<ErrorMessage> invocationErrors;

	UnregisterResult(boolean success, Procedure proc) {
		this(success, proc, null);
	}

	UnregisterResult(boolean success, Procedure proc,
			@Nullable List<ErrorMessage> invocationErrors) {
		this.success = success;
		this.procedure = proc.getProcedure();
		this.registrationId = proc.getRegistrationId();
		this.invocationErrors = invocationErrors;
	}

	boolean isSuccess() {
		return this.success;
	}

	String getProcedure() {
		return this.procedure;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	@Nullable
	List<ErrorMessage> getInvocationErrors() {
		return this.invocationErrors;
	}

}
