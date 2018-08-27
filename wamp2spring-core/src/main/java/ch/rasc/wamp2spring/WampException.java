/**
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.wamp2spring;

import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * User exception that can be thrown at procedure invocation
 */
public class WampException extends Exception {

	public static class Builder {
		private List<Object> arguments;
		private Map<String, Object> argumentsKw;
		private Throwable throwable;

		public Builder arguments(List<Object> param) {
			this.arguments = param;
			return this;
		}

		public Builder argumentsKw(Map<String, Object> param) {
			this.argumentsKw = param;
			return this;
		}

		public Builder throwable(Throwable param) {
			this.throwable = param;
			return this;
		}

		public WampException build(String error) {
			return new WampException(error, arguments, argumentsKw, throwable);
		}
	}

	private final String uri;
	private final List<Object> arguments;
	private final Map<String, Object> argumentsKw;

	protected WampException(String uri,
							@Nullable List<Object> arguments,
							@Nullable Map<String, Object> argumentsKw,
							@Nullable Throwable throwable) {
		super(throwable);
		this.uri = uri;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public String getUri() {
		return uri;
	}

	public @Nullable List<Object> getArguments() {
		return arguments;
	}

	public @Nullable Map<String, Object> getArgumentsKw() {
		return argumentsKw;
	}
}
