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

package ch.rasc.wamp2spring;

/**
 * Enumeration of all possible WAMP errors
 */
public enum WampError {

	NO_SUCH_PROCEDURE("wamp.error.no_such_procedure"),
	PROCEDURE_ALREADY_EXISTS("wamp.error.procedure_already_exists"),
	NO_SUCH_REGISTRATION("wamp.error.no_such_registration"),
	NO_SUCH_SUBSCRIPTION("wamp.error.no_such_subscription"),
	GOODBYE_AND_OUT("wamp.error.goodbye_and_out"),
	NETWORK_FAILURE("wamp.error.network_failure"),
	INVALID_ARGUMENT("wamp.error.invalid_argument"),
	NOT_AUTHORIZED("wamp.error.not_authorized"),
	OPTION_NOT_ALLOWED("wamp.error.option_not_allowed"),
	DISCLOSE_ME_DISALLOWED("wamp.error.option_disallowed.disclose_me");

	private final String externalValue;

	private WampError(String externalValue) {
		this.externalValue = externalValue;
	}

	/**
	 * Returns the external value of the error message used for message serialization
	 * @return the external value
	 */
	public String getExternalValue() {
		return this.externalValue;
	}

}
