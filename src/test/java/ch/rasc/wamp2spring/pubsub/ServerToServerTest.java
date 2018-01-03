/**
 * Copyright 2017-2018 the original author or authors.
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
package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.config.EnableWamp;
import ch.rasc.wamp2spring.testsupport.BaseWampTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		classes = ServerToServerTest.Config.class)
public class ServerToServerTest extends BaseWampTest {

	@Autowired
	private ServerToServerService serverToServerService;

	@Test
	public void test() throws Exception {
		WampPublisher wp = this.serverToServerService.getWampPublisher();
		wp.publish(wp.publishMessageBuilder("sum").notExcludeMe().addArgument(1)
				.addArgument(2).build());
		TimeUnit.SECONDS.sleep(2);
		assertThat(this.serverToServerService.isCalled()).isTrue();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableWamp
	static class Config {
		@Bean
		public ServerToServerService serverToServerService(WampPublisher wampPublisher) {
			return new ServerToServerService(wampPublisher);
		}
	}
}
