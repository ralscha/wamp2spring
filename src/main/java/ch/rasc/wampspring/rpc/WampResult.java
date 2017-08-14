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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class WampResult {

	@Nullable
	private List<Object> results;

	@Nullable
	private Map<String, Object> resultsKw;

	public static WampResult createKw(String k, Object v) {
		Map<String, Object> kw = new HashMap<>();
		kw.put(k, v);
		return new WampResult(kw);
	}

	@SafeVarargs
	public static <T> WampResult create(T... a) {
		List<T> arg = new ArrayList<>();
		arg.addAll(Arrays.asList(a));
		return new WampResult(arg);
	}

	public WampResult() {
		this(null, null);
	}

	@SuppressWarnings("unchecked")
	public <T> WampResult(@Nullable List<T> results) {
		this((List<Object>) results, null);
	}

	public WampResult(@Nullable Map<String, Object> resultsKw) {
		this(null, resultsKw);
	}

	public WampResult(@Nullable List<Object> results,
			@Nullable Map<String, Object> resultsKw) {
		this.results = results;
		this.resultsKw = resultsKw;
	}

	public WampResult add(Object value) {
		if (this.results == null) {
			this.results = new ArrayList<>();
		}

		this.results.add(value);
		return this;
	}

	public WampResult add(String key, Object value) {
		if (this.resultsKw == null) {
			this.resultsKw = new HashMap<>();
		}

		this.resultsKw.put(key, value);
		return this;
	}

	@Nullable
	public List<Object> getResults() {
		return this.results;
	}

	public void setResults(List<Object> results) {
		this.results = results;
	}

	@Nullable
	public Map<String, Object> getResultsKw() {
		return this.resultsKw;
	}

	public void setResultsKw(Map<String, Object> resultsKw) {
		this.resultsKw = resultsKw;
	}

	@Override
	public String toString() {
		return "WampResult [results=" + this.results + ", resultsKw=" + this.resultsKw
				+ "]";
	}

}
