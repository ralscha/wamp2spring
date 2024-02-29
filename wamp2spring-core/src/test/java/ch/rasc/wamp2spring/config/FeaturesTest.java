/**
 * Copyright the original author or authors.
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
		feat.disable(Feature.DEALER_CALLER_IDENTIFICATION);
		feat.disable(Feature.BROKER_PUBLISHER_IDENTIFICATION);

		assertThat(feat.isEnabled(Feature.DEALER)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER)).isTrue();
		assertThat(feat.isEnabled(Feature.DEALER_CALLER_IDENTIFICATION)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_PUBLISHER_EXCLUSION)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)).isFalse();
		assertThat(feat.isEnabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)).isTrue();
		assertThat(feat.isEnabled(Feature.BROKER_EVENT_RETENTION)).isTrue();

		assertThat(feat.isDisabled(Feature.DEALER)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER)).isFalse();
		assertThat(feat.isDisabled(Feature.DEALER_CALLER_IDENTIFICATION)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING))
				.isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_PUBLISHER_EXCLUSION)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)).isTrue();
		assertThat(feat.isDisabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)).isFalse();
		assertThat(feat.isDisabled(Feature.BROKER_EVENT_RETENTION)).isFalse();
	}

	@Test
	public void testEnabledDealerFeatures() {
		Features feat = new Features();
		assertThat(feat.enabledDealerFeatures())
				.containsExactly(Feature.DEALER_CALLER_IDENTIFICATION);

		feat.disable(Feature.DEALER_CALLER_IDENTIFICATION);
		assertThat(feat.enabledDealerFeatures()).isEmpty();
	}

	@Test
	public void testEnabledBrokerFeatures() {
		Features feat = new Features();
		assertThat(feat.enabledBrokerFeatures()).containsExactly(
				Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING,
				Feature.BROKER_PUBLISHER_EXCLUSION,
				Feature.BROKER_PUBLISHER_IDENTIFICATION,
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION);

		feat.disable(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(
				Feature.BROKER_PUBLISHER_EXCLUSION,
				Feature.BROKER_PUBLISHER_IDENTIFICATION,
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION);

		feat.disable(Feature.BROKER_PUBLISHER_EXCLUSION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(
				Feature.BROKER_PUBLISHER_IDENTIFICATION,
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION);

		feat.disable(Feature.BROKER_PUBLISHER_IDENTIFICATION);
		assertThat(feat.enabledBrokerFeatures()).containsExactly(
				Feature.BROKER_PATTERN_BASED_SUBSCRIPTION,
				Feature.BROKER_EVENT_RETENTION);

		feat.disable(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION);
		assertThat(feat.enabledBrokerFeatures())
				.containsExactly(Feature.BROKER_EVENT_RETENTION);

		feat.disable(Feature.BROKER_EVENT_RETENTION);
		assertThat(feat.enabledBrokerFeatures()).isEmpty();
	}
}
