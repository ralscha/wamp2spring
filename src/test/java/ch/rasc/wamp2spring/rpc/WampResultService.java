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
package ch.rasc.wamp2spring.rpc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.rpc.WampResult;

public class WampResultService {
	private final Set<String> called = new HashSet<>();

	@WampProcedure(name = "sum")
	public WampResult sum(int a, int b) {
		this.called.add("sum");
		return WampResult.create(a + b);
	}

	@WampProcedure(name = "toDto")
	public WampResult toDto(long id, String name) {
		this.called.add("toDto");
		return WampResult.create(new TestDto(id, name));
	}

	@WampProcedure(name = "toTwoDto")
	public WampResult toTwoDto(long id, String name) {
		this.called.add("toTwoDto");
		return WampResult.create(new TestDto(id, name)).add(new TestDto(1000 + id, name));
	}

	@WampProcedure(name = "toDtoKw")
	public WampResult toDtoKw(long id, String name) {
		this.called.add("toDtoKw");
		WampResult wr = new WampResult();
		ObjectMapper om = new ObjectMapper();
		wr.setResultsKw(om.convertValue(new TestDto(id, name), Map.class));
		return wr;
	}

	@WampProcedure(name = "two")
	public WampResult two(String first, String second) {
		this.called.add("two");
		return WampResult.create(first.toUpperCase(), second.toUpperCase());
	}

	@WampProcedure(name = "empty")
	public WampResult empty() {
		this.called.add("empty");
		return new WampResult();
	}

	@WampProcedure(name = "twoKw")
	public WampResult twoKw(String first, String second) {
		this.called.add("twoKw");
		return WampResult.createKw("1", first.toUpperCase()).add("2",
				second.toUpperCase());
	}

	@WampProcedure(name = "mix")
	public WampResult mixedResult(BigDecimal amount, String text) {
		this.called.add("mixedResult");
		return WampResult
				.createKw("5%",
						amount.multiply(new BigDecimal("1.05")).setScale(1,
								RoundingMode.HALF_UP))
				.add("10%",
						amount.multiply(new BigDecimal("1.1")).setScale(1,
								RoundingMode.HALF_UP))
				.add(text.charAt(0)).add(text.charAt(1)).add(text.charAt(2));
	}

	public boolean isCalled(String method) {
		boolean contains = this.called.contains(method);
		this.called.remove(method);
		return contains;
	}

}