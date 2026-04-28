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
package ch.rasc.wamp2spring.util;

import java.io.IOException;
import java.util.Base64;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Helpers for WAMP-specific JSON encoding conventions.
 */
public final class WampJson {

	private static final char JSON_BINARY_PREFIX = '\0';

	private WampJson() {
		// utility class
	}

	public static ObjectMapper createJsonObjectMapper() {
		return configureJsonObjectMapper(JsonMapper.builderWithJackson2Defaults().build());
	}

	public static ObjectMapper configureJsonObjectMapper(ObjectMapper objectMapper) {
		SimpleModule module = new SimpleModule();
		module.addSerializer(byte[].class, new WampJsonBinarySerializer());
		return objectMapper.rebuild().addModule(module).build();
	}

	@Nullable public static Object decodeBinaryValue(@Nullable String value) throws IOException {
		if (value == null || value.isEmpty() || value.charAt(0) != JSON_BINARY_PREFIX) {
			return value;
		}

		try {
			return Base64.getDecoder().decode(value.substring(1));
		}
		catch (IllegalArgumentException ex) {
			throw new IOException("Invalid WAMP JSON binary value.", ex);
		}
	}

	private static final class WampJsonBinarySerializer extends StdSerializer<byte[]> {

		private WampJsonBinarySerializer() {
			super(byte[].class);
		}

		@Override
		public void serialize(byte[] value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(JSON_BINARY_PREFIX + Base64.getEncoder().encodeToString(value));
		}

	}

}