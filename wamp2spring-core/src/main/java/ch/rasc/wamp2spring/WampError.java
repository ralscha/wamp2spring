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
package ch.rasc.wamp2spring;

/**
 * Enumeration of all possible WAMP errors
 */
public enum WampError {

	// Basic Profile - error URIs (spec §8)
	NO_SUCH_PROCEDURE("wamp.error.no_such_procedure"), PROCEDURE_ALREADY_EXISTS("wamp.error.procedure_already_exists"),
	NO_SUCH_REGISTRATION("wamp.error.no_such_registration"), NO_SUCH_SUBSCRIPTION("wamp.error.no_such_subscription"),
	INVALID_URI("wamp.error.invalid_uri"), INVALID_ARGUMENT("wamp.error.invalid_argument"),
	NOT_AUTHORIZED("wamp.error.not_authorized"), AUTHORIZATION_FAILED("wamp.error.authorization_failed"),
	NO_SUCH_AUTH_METHOD("wamp.error.no_such_authmethod"), NO_SUCH_REALM("wamp.error.no_such_realm"),
	PROTOCOL_VIOLATION("wamp.error.protocol_violation"), NETWORK_FAILURE("wamp.error.network_failure"),
	OPTION_NOT_ALLOWED("wamp.error.option_not_allowed"),
	DISCLOSE_ME_DISALLOWED("wamp.error.option_disallowed.disclose_me"),

	// Advanced Profile - error URIs (spec §18)
	NO_SUCH_SESSION("wamp.error.no_such_session"), CANCELED("wamp.error.canceled"), TIMEOUT("wamp.error.timeout"),
	UNAVAILABLE("wamp.error.unavailable"), NO_AVAILABLE_CALLEE("wamp.error.no_available_callee"),
	FEATURE_NOT_SUPPORTED("wamp.error.feature_not_supported"),

	// Session close reasons - wamp.close.* namespace (spec §8 / §18)
	GOODBYE_AND_OUT("wamp.close.goodbye_and_out"), SYSTEM_SHUTDOWN("wamp.close.system_shutdown"),
	CLOSE_REALM("wamp.close.close_realm"), CLOSE_KILLED("wamp.close.killed");

	private final String externalValue;

	WampError(String externalValue) {
		this.externalValue = externalValue;
	}

	/**
	 * Returns the external value of the error message used for message serialisation
	 * @return the external value
	 */
	public String getExternalValue() {
		return this.externalValue;
	}

}
