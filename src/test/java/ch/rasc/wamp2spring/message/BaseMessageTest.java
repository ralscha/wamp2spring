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
package ch.rasc.wamp2spring.message;

import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BaseMessageTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final JsonFactory jsonFactory = new MappingJsonFactory(this.objectMapper);

	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	public JsonFactory getJsonFactory() {
		return this.jsonFactory;
	}

	public Set<Long> toSet(Long... numbers) {
		return new HashSet<>(Arrays.asList(numbers));
	}

	String serializeToJson(WampMessage message) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = getJsonFactory().createGenerator(bos)) {
			generator.writeStartArray();
			message.serialize(generator);
			generator.writeEndArray();
			generator.close();
			return new String(bos.toByteArray());
		}
		catch (IOException e) {
			fail(e.getMessage());
			return null;
		}
	}

}