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
package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.stereotype.Service;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.annotation.WampListener;

@Service
public class ServerToServerService {

	private final WampPublisher wampPublisher;

	private boolean called = false;

	public ServerToServerService(WampPublisher wampPublisher) {
		this.wampPublisher = wampPublisher;
	}

	public WampPublisher getWampPublisher() {
		return this.wampPublisher;
	}

	@WampListener("sum")
	public void sum(int a, int b) {
		assertThat(a).isEqualTo(1);
		assertThat(b).isEqualTo(2);
		this.called = true;
	}

	public boolean isCalled() {
		return this.called;
	}

}
