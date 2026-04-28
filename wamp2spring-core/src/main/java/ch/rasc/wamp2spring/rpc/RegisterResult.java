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

class RegisterResult {

	private final boolean success;

	private final boolean created;

	private final long registrationId;

	private RegisterResult(boolean success, boolean created, long registrationId) {
		this.success = success;
		this.created = created;
		this.registrationId = registrationId;
	}

	static RegisterResult success(long registrationId, boolean created) {
		return new RegisterResult(true, created, registrationId);
	}

	static RegisterResult failed() {
		return new RegisterResult(false, false, -1L);
	}

	boolean isSuccess() {
		return this.success;
	}

	boolean isCreated() {
		return this.created;
	}

	long getRegistrationId() {
		return this.registrationId;
	}

}