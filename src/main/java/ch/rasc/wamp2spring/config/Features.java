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
package ch.rasc.wamp2spring.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum Features {
	INSTANCE;

	private final EnumSet<Feature> enabledFeatures = EnumSet.allOf(Feature.class);

	public static void disable(Feature feature) {
		INSTANCE.enabledFeatures.remove(feature);
	}

	static EnumSet<Feature> getEnabledFeatures() {
		return INSTANCE.enabledFeatures;
	}

	public static boolean isEnabled(Feature feature) {
		return INSTANCE.enabledFeatures.contains(feature);
	}

	public static boolean isDisabled(Feature feature) {
		return !INSTANCE.enabledFeatures.contains(feature);
	}

	public static List<Feature> enabledDealerFeatures() {
		List<Feature> dealerFeatures = new ArrayList<>();
		if (isEnabled(Feature.DEALER_CALLER_IDENTIFICATION)) {
			dealerFeatures.add(Feature.DEALER_CALLER_IDENTIFICATION);
		}
		return dealerFeatures;
	}

	public static List<Feature> enabledBrokerFeatures() {
		List<Feature> brokerFeatures = new ArrayList<>();
		if (isEnabled(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING)) {
			brokerFeatures.add(Feature.BROKER_SUBSCRIBER_BLACKWHITE_LISTING);
		}
		if (isEnabled(Feature.BROKER_PUBLISHER_EXCLUSION)) {
			brokerFeatures.add(Feature.BROKER_PUBLISHER_EXCLUSION);
		}
		if (isEnabled(Feature.BROKER_PUBLISHER_IDENTIFICATION)) {
			brokerFeatures.add(Feature.BROKER_PUBLISHER_IDENTIFICATION);
		}
		if (isEnabled(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION)) {
			brokerFeatures.add(Feature.BROKER_PATTERN_BASED_SUBSCRIPTION);
		}
		if (isEnabled(Feature.BROKER_EVENT_RETENTION)) {
			brokerFeatures.add(Feature.BROKER_EVENT_RETENTION);
		}
		return brokerFeatures;
	}
}
