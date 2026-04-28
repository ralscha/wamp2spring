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
package ch.rasc.wamp2spring.servlet.longpoll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;

public class LongpollTransportRegistryTest {

	@Test
	public void createValidatesProtocolAndStoresTransport() {
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofSeconds(10));

		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);

		assertThat(transport.getTransportId()).isNotBlank();
		assertThat(registry.get(transport.getTransportId())).isSameAs(transport);
		assertThatThrownBy(() -> registry.create("wamp.2.invalid", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported longpoll protocol");
	}

	@Test
	public void removeIdleTransportsEvictsExpiredEntries() throws InterruptedException {
		LongpollTransportRegistry registry = new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, new ObjectMapper()), 4, Duration.ofSeconds(5),
				Duration.ofMillis(1));

		LongpollTransport transport = registry.create(WampSubProtocolHandler.JSON_PROTOCOL, null);
		assertThat(registry.getTransports()).hasSize(1);
		TimeUnit.MILLISECONDS.sleep(5);

		registry.removeIdleTransports();

		assertThat(registry.remove(transport.getTransportId())).isNull();
		assertThat(registry.getTransports()).isEmpty();
	}

}