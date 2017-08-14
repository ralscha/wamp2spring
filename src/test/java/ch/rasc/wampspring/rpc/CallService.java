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
package ch.rasc.wampspring.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import ch.rasc.wampspring.annotation.WampProcedure;
import ch.rasc.wampspring.message.CallMessage;

public class CallService {
	private final Set<String> called = new HashSet<>();

	@WampProcedure
	public int sum(int a, int b) {
		this.called.add("sum");
		return a + b;
	}

	@WampProcedure("sum2")
	public int sumDifferent1(int a, int b) {
		this.called.add("sumDifferent1");
		return a + b;
	}

	@WampProcedure(name = "sum3")
	public int sumDifferent2(int a, int b) {
		this.called.add("sumDifferent2");
		return a + b;
	}

	@WampProcedure
	public void noReturn(String arg1, Integer arg2) {
		assertThat(arg1).isEqualTo("name");
		assertThat(arg2).isEqualTo(23);
		this.called.add("noReturn");
	}

	@WampProcedure
	public long call(CallMessage callMessage) {
		this.called.add("call");
		assertThat(callMessage.getArguments()).isNull();
		assertThat(callMessage.getArgumentsKw()).isNull();
		assertThat(callMessage.getProcedure()).isEqualTo("callService.call");
		return callMessage.getRequestId();
	}

	@WampProcedure
	public String noParams() {
		this.called.add("noParams");
		return "nothing here";
	}

	@WampProcedure
	public Integer error(String argument) {
		this.called.add("error");
		assertThat(argument).isEqualTo("theArgument");
		throw new NullPointerException();
	}

	@WampProcedure
	public String callWithDto(TestDto testDto) {
		this.called.add("callWithDto");
		assertThat(testDto.getName()).isEqualTo("Hi");
		assertThat(testDto.getId()).isEqualTo(1);
		return testDto.getName().toUpperCase();
	}

	@WampProcedure
	public String callWithDtoAndMessage(TestDto testDto, CallMessage callMessage,
			String secondArgument) {
		this.called.add("callWithDtoAndMessage");
		assertThat(callMessage).isNotNull();
		assertThat(testDto.getName()).isEqualTo("Hi");
		assertThat(testDto.getId()).isEqualTo(2);
		assertThat(secondArgument).isEqualTo("the_second_argument");
		return testDto.getName().toUpperCase() + "/" + secondArgument;
	}

	public boolean isCalled(String method) {
		boolean contains = this.called.contains(method);
		this.called.remove(method);
		return contains;
	}

}