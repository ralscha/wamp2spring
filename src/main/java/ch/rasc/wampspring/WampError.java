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
package ch.rasc.wampspring;

public enum WampError {

	NO_SUCH_PROCEDURE("wamp.error.no_such_procedure"),
	PROCEDURE_ALREADY_EXISTS("wamp.error.procedure_already_exists"),
	NO_SUCH_REGISTRATION("wamp.error.no_such_registration"),
	NO_SUCH_SUBSCRIPTION("wamp.error.no_such_subscription"),
	GOODBYE_AND_OUT("wamp.error.goodbye_and_out"),
	NETWORK_FAILURE("wamp.error.network_failure"),
	INVALID_ARGUMENT("wamp.error.invalid_argument");

	private final String externalValue;

	private WampError(String externalValue) {
		this.externalValue = externalValue;
	}

	public String getExternalValue() {
		return this.externalValue;
	}

}
