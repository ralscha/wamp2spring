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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class FeaturesTest {

	@Test
	public void testAllFeatures() {
		Features feat = new Features();
		for (Feature f : Feature.values()) {
			assertThat(feat.isEnabled(f)).isTrue();
			assertThat(feat.isDisabled(f)).isFalse();
		}
	}

	@Test
	public void testDisableFeatures() {
		Features feat = new Features();
		feat.disable(Feature.DEALER_CALL_CANCELING);
		feat.disable(Feature.DEALER_CALL_TIMEOUT);
		feat.disable(Feature.DEALER_PROGRESSIVE_CALL_RESULTS);
		feat.disable(Feature.DEALER_CALLER_IDENTIFICATION);
		feat.disable(Feature.DEALER_PATTERN_BASED_REGISTRATION);
		feat.disable(Feature.DEALER_SHARED_REGISTRATION);
		feat.disable(Feature.DEALER_REGISTRATION_META_API);
		feat.disable(Feature.DEALER_SESSION_META_API);
		feat.disable(Feature.DEALER_TESTAMENT_META_API);
		feat.disable(Feature.DEALER_REGISTRATION_REVOCATION);
		feat.disable(Feature.BROKER_PUBLISHER_IDENTIFICATION);
		feat.disable(Feature.BROKER_EVENT_HISTORY);
		feat.disable(Feature.BROKER_SESSION_META_API);
		feat.disable(Feature.BROKER_SUBSCRIPTION_META_API);
		feat.disable(Feature.BROKER_SUBSCRIPTION_REVOCATION);

		assertThat(feat.isEnabled(Feature.DEALER)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER)).isTrue();
		assertThat(feat.isEnabled(Feature.DEALER_CALL_CANCELING)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_CALL_TIMEOUT)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_PROGRESSIVE_CALL_RESULTS)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_CALLER_IDENTIFICATION)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_PATTERN_BASED_REGISTRATION)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_SHARED_REGISTRATION)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_REGISTRATION_META_API)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_SESSION_META_API)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_TESTAMENT_META_API)).isFalse();
		assertThat(feat.isEnabled(Feature.DEALER_REGISTRATION_REVOCATION)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_PUBLISHER_EXCLUSION)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_EVENT_RETENTION)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_EVENT_HISTORY)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_SESSION_META_API)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_SUBSCRIPTION_META_API)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_SUBSCRIPTION_REVOCATION)).isFalse();

		assertThat(feat.isDisabled(Feature.DEALER)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER)).isFalse();
		assertThat(feat.isDisabled(Feature.DEALER_CALL_CANCELING)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_CALL_TIMEOUT)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_PROGRESSIVE_CALL_RESULTS)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_CALLER_IDENTIFICATION)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_PATTERN_BASED_REGISTRATION)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_SHARED_REGISTRATION)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_REGISTRATION_META_API)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_SESSION_META_API)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_TESTAMENT_META_API)).isTrue();
		assertThat(feat.isDisabled(Feature.DEALER_REGISTRATION_REVOCATION)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_PUBLISHER_EXCLUSION)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_EVENT_RETENTION)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_EVENT_HISTORY)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_SESSION_META_API)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_SUBSCRIPTION_META_API)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_SUBSCRIPTION_REVOCATION)).isTrue();
	}

	@Test
	public void testEnabledDealerFeatures() {
		Features feat = new Features();
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_CALL_CANCELING,
				Feature.DEALER_CALL_TIMEOUT, Feature.DEALER_PROGRESSIVE_CALL_RESULTS,
				Feature.DEALER_CALLER_IDENTIFICATION, Feature.DEALER_PATTERN_BASED_REGISTRATION,
				Feature.DEALER_SHARED_REGISTRATION, Feature.DEALER_CALL_REROUTE, Feature.DEALER_REGISTRATION_META_API,
				Feature.DEALER_SESSION_META_API, Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_CALL_CANCELING);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_CALL_TIMEOUT,
				Feature.DEALER_PROGRESSIVE_CALL_RESULTS, Feature.DEALER_CALLER_IDENTIFICATION,
				Feature.DEALER_PATTERN_BASED_REGISTRATION, Feature.DEALER_SHARED_REGISTRATION,
				Feature.DEALER_CALL_REROUTE, Feature.DEALER_REGISTRATION_META_API, Feature.DEALER_SESSION_META_API,
				Feature.DEALER_TESTAMENT_META_API, Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_CALL_TIMEOUT);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_PROGRESSIVE_CALL_RESULTS,
				Feature.DEALER_CALLER_IDENTIFICATION, Feature.DEALER_PATTERN_BASED_REGISTRATION,
				Feature.DEALER_SHARED_REGISTRATION, Feature.DEALER_CALL_REROUTE, Feature.DEALER_REGISTRATION_META_API,
				Feature.DEALER_SESSION_META_API, Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_PROGRESSIVE_CALL_RESULTS);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_CALLER_IDENTIFICATION,
				Feature.DEALER_PATTERN_BASED_REGISTRATION, Feature.DEALER_SHARED_REGISTRATION,
				Feature.DEALER_CALL_REROUTE, Feature.DEALER_REGISTRATION_META_API, Feature.DEALER_SESSION_META_API,
				Feature.DEALER_TESTAMENT_META_API, Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_CALLER_IDENTIFICATION);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_PATTERN_BASED_REGISTRATION,
				Feature.DEALER_SHARED_REGISTRATION, Feature.DEALER_CALL_REROUTE, Feature.DEALER_REGISTRATION_META_API,
				Feature.DEALER_SESSION_META_API, Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_PATTERN_BASED_REGISTRATION);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_SHARED_REGISTRATION,
				Feature.DEALER_CALL_REROUTE,
				Feature.DEALER_REGISTRATION_META_API, Feature.DEALER_SESSION_META_API,
				Feature.DEALER_TESTAMENT_META_API, Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_SHARED_REGISTRATION);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_CALL_REROUTE,
				Feature.DEALER_REGISTRATION_META_API,
				Feature.DEALER_SESSION_META_API, Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_CALL_REROUTE);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_REGISTRATION_META_API,
				Feature.DEALER_SESSION_META_API, Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_REGISTRATION_META_API);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_SESSION_META_API,
				Feature.DEALER_TESTAMENT_META_API, Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_SESSION_META_API);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_TESTAMENT_META_API,
				Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_TESTAMENT_META_API);
		assertThat(feat.enabledDealerFeatures()).containsExactly(Feature.DEALER_REGISTRATION_REVOCATION);

		feat.disable(Feature.DEALER_REGISTRATION_REVOCATION);
		assertThat(feat.enabledDealerFeatures()).isEmpty();
	}

	@Test
	public void testEnabledBrokerFeatures() {
		Features feat = new Features();
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING,
				Feature.BROKER_PUBLISHER_EXCLUSION, Feature.BROKER_PUBLISHER_IDENTIFICATION,
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION, Feature.BROKER_EVENT_RETENTION, Feature.BROKER_EVENT_HISTORY,
				Feature.BROKER_SESSION_META_API, Feature.BROKER_SUBSCRIPTION_META_API,
				Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_PUBLISHER_EXCLUSION,
				Feature.BROKER_PUBLISHER_IDENTIFICATION, Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION, Feature.BROKER_EVENT_HISTORY, Feature.BROKER_SESSION_META_API,
				Feature.BROKER_SUBSCRIPTION_META_API, Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_PUBLISHER_EXCLUSION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_PUBLISHER_IDENTIFICATION,
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION, Feature.BROKER_EVENT_RETENTION, Feature.BROKER_EVENT_HISTORY,
				Feature.BROKER_SESSION_META_API, Feature.BROKER_SUBSCRIPTION_META_API,
				Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_PUBLISHER_IDENTIFICATION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION, Feature.BROKER_EVENT_HISTORY, Feature.BROKER_SESSION_META_API,
				Feature.BROKER_SUBSCRIPTION_META_API, Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_EVENT_RETENTION,
				Feature.BROKER_EVENT_HISTORY, Feature.BROKER_SESSION_META_API, Feature.BROKER_SUBSCRIPTION_META_API,
				Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_EVENT_RETENTION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_EVENT_HISTORY,
				Feature.BROKER_SESSION_META_API, Feature.BROKER_SUBSCRIPTION_META_API,
				Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_EVENT_HISTORY);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_SESSION_META_API,
				Feature.BROKER_SUBSCRIPTION_META_API, Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_SESSION_META_API);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_SUBSCRIPTION_META_API,
				Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_SUBSCRIPTION_META_API);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(Feature.BROKER_SUBSCRIPTION_REVOCATION);

		feat.disable(Feature.BROKER_SUBSCRIPTION_REVOCATION);
		assertThat(feat.enabledBrokerFeatures()).isEmpty();
	}

}
