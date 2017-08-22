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
package ch.rasc.wamp2spring.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import ch.rasc.wamp2spring.annotation.WampListener;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.rpc.TestDto;

@Service
public class ClientToServerService {

	private final Set<String> called = new HashSet<>();

	@WampListener
	public void sum(int a, int b) {
		assertThat(a).isEqualTo(1);
		assertThat(b).isEqualTo(2);
		this.called.add("sum");
	}

	@WampListener("sum2")
	public void sum2(int a, int b) {
		assertThat(a).isEqualTo(3);
		assertThat(b).isEqualTo(4);
		this.called.add("sum2");
	}

	@WampListener(topic = "sum3")
	public void sum3(int a, int b) {
		assertThat(a).isEqualTo(5);
		assertThat(b).isEqualTo(6);
		this.called.add("sum3");
	}

	@WampListener
	public void listener(EventMessage eventMessage) {
		this.called.add("listener");
		assertThat(eventMessage.getArguments()).containsExactly(8, 9);
		assertThat(eventMessage.getArgumentsKw()).isNull();
		assertThat(eventMessage.getTopic()).isEqualTo("clientToServerService.listener");
	}

	@WampListener
	public void noParams() {
		this.called.add("noParams");
	}

	@WampListener
	public void error(String argument) {
		this.called.add("error");
		assertThat(argument).isEqualTo("theArgument");
		throw new NullPointerException();
	}

	@WampListener
	public void listenerWithDto(TestDto testDto) {
		this.called.add("listenerWithDto");
		assertThat(testDto.getName()).isEqualTo("Hi");
		assertThat(testDto.getId()).isEqualTo(1);
	}

	@WampListener
	public void listenerWithDtoAndMessage(TestDto testDto, EventMessage eventMessage,
			String secondArgument) {
		this.called.add("listenerWithDtoAndMessage");
		assertThat(eventMessage).isNotNull();
		assertThat(testDto.getName()).isEqualTo("Hi");
		assertThat(testDto.getId()).isEqualTo(2);
		assertThat(secondArgument).isEqualTo("the_second_argument");
	}

	@WampListener(topic = "news", match = MatchPolicy.PREFIX)
	public void prefixListener(int arg) {
		this.called.add("prefixListener");
		assertThat(arg).isEqualTo(23);
	}

	@WampListener(topic = "crud..create", match = MatchPolicy.WILDCARD)
	public void crudCreateListener(String id) {
		this.called.add("crudCreateListener");
		assertThat(id).isEqualTo("111");
	}

	public boolean isCalled(String method) {
		boolean contains = this.called.contains(method) && this.called.size() == 1;
		this.called.remove(method);
		return contains;
	}

}
