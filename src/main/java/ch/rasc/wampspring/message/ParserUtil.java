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
package ch.rasc.wampspring.message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class ParserUtil {
	@Nullable
	public static List<Object> readArray(JsonParser jp) throws IOException {
		if (jp.currentToken() != JsonToken.START_ARRAY) {
			return null;
		}

		List<Object> result = new ArrayList<>();
		JsonToken token = jp.nextToken();
		while (token != JsonToken.END_ARRAY) {

			if (token == JsonToken.START_ARRAY) {
				result.add(readArray(jp));
			}
			else if (token == JsonToken.START_OBJECT) {
				result.add(readObject(jp));
			}
			else {
				result.add(getValue(jp));
			}

			token = jp.nextToken();
		}

		return result;
	}

	@Nullable
	public static Map<String, Object> readObject(JsonParser jp) throws IOException {
		if (jp.currentToken() != JsonToken.START_OBJECT) {
			return null;
		}

		Map<String, Object> result = new HashMap<>();
		JsonToken token = jp.nextToken();
		while (token != JsonToken.END_OBJECT) {
			String key = jp.getValueAsString();
			token = jp.nextToken();

			if (token == JsonToken.START_ARRAY) {
				result.put(key, readArray(jp));
			}
			else if (token == JsonToken.START_OBJECT) {
				result.put(key, readObject(jp));
			}
			else {
				result.put(key, getValue(jp));
			}

			token = jp.nextToken();
		}

		return result;

	}

	@Nullable
	private static Object getValue(JsonParser jp) throws IOException {
		switch (jp.currentToken()) {
		case VALUE_FALSE:
		case VALUE_TRUE:
			return jp.getBooleanValue();
		case VALUE_STRING:
			return jp.getValueAsString();
		case VALUE_NUMBER_INT:
			switch (jp.getNumberType()) {
			case INT:
				return jp.getIntValue();
			case LONG:
				return jp.getLongValue();
			case BIG_INTEGER:
				return jp.getBigIntegerValue();
			default:
				return null;
			}
		case VALUE_NUMBER_FLOAT:
			switch (jp.getNumberType()) {
			case FLOAT:
				return jp.getFloatValue();
			case DOUBLE:
				return jp.getDoubleValue();
			case BIG_DECIMAL:
				return jp.getDecimalValue();
			default:
				return null;
			}
		default:
			return null;
		}
	}
}
