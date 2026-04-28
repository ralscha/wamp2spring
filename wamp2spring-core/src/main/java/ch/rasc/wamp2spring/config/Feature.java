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
package ch.rasc.wamp2spring.config;

/**
 * Enumeration of all implemented features in wamp2spring
 */
public enum Feature {

	DEALER("dealer"), BROKER("broker"), DEALER_CALL_CANCELING("call_canceling"), DEALER_CALL_TIMEOUT("call_timeout"),
	DEALER_PROGRESSIVE_CALL_RESULTS("progressive_call_results"), DEALER_CALLER_IDENTIFICATION("caller_identification"),
	DEALER_PATTERN_BASED_REGISTRATION("pattern_based_registration"), DEALER_SHARED_REGISTRATION("shared_registration"),
	DEALER_REGISTRATION_META_API("registration_meta_api"), DEALER_SESSION_META_API("session_meta_api"),
	DEALER_TESTAMENT_META_API("testament_meta_api"), DEALER_REGISTRATION_REVOCATION("registration_revocation"),
	BROKER_SUBSCRIBER_BLACKWHITE_LISTING("subscriber_blackwhite_listing"),
	BROKER_PUBLISHER_EXCLUSION("publisher_exclusion"), BROKER_PUBLISHER_IDENTIFICATION("publisher_identification"),
	BROKER_PATTERN_BASED_SUBSCRIPTION("pattern_based_subscription"), BROKER_EVENT_RETENTION("event_retention"),
	BROKER_SESSION_META_API("session_meta_api"), BROKER_SUBSCRIPTION_META_API("subscription_meta_api"),
	BROKER_SUBSCRIPTION_REVOCATION("subscription_revocation");

	private final String externalValue;

	Feature(String externalValue) {
		this.externalValue = externalValue;
	}

	public String getExternalValue() {
		return this.externalValue;
	}

}
