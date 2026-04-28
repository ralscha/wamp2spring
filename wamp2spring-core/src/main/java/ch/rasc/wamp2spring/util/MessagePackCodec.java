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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;

import ch.rasc.wamp2spring.message.WampMessage;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bridges MessagePack transport frames through msgpack-core while the rest of the WAMP
 * stack uses Jackson 3 for message parsing and generation.
 */
public final class MessagePackCodec {

	private MessagePackCodec() {
		// utility class
	}

	public static byte[] toJson(byte[] messagePack, ObjectMapper jsonObjectMapper) throws IOException {
		try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(messagePack)) {
			Object value = unpackValue(unpacker);
			return jsonObjectMapper.writeValueAsBytes(value);
		}
	}

	public static WampMessage deserializeWampMessage(byte[] messagePack, ObjectMapper jsonObjectMapper)
			throws IOException {
		try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(messagePack)) {
			JsonNode root = jsonObjectMapper.valueToTree(unpackValue(unpacker));
			try (JsonParser parser = jsonObjectMapper.treeAsTokens(root)) {
				return WampMessage.deserialize(parser);
			}
		}
	}

	public static byte[] fromJson(byte[] json, ObjectMapper jsonObjectMapper) throws IOException {
		Object value = normalizeJsonValue(jsonObjectMapper.readValue(json, Object.class));
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				MessagePacker packer = MessagePack.newDefaultPacker(outputStream)) {
			packValue(packer, value);
			packer.flush();
			return outputStream.toByteArray();
		}
	}

	@Nullable private static Object normalizeJsonValue(@Nullable Object value) throws IOException {
		if (value instanceof String stringValue) {
			return WampJson.decodeBinaryValue(stringValue);
		}
		if (value instanceof List<?> listValue) {
			List<Object> normalizedValues = new ArrayList<>(listValue.size());
			for (Object element : listValue) {
				normalizedValues.add(normalizeJsonValue(element));
			}
			return normalizedValues;
		}
		if (value instanceof Map<?, ?> mapValue) {
			Map<String, Object> normalizedValues = new LinkedHashMap<>(mapValue.size());
			for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
				normalizedValues.put(String.valueOf(entry.getKey()), normalizeJsonValue(entry.getValue()));
			}
			return normalizedValues;
		}
		return value;
	}

	private static void packValue(MessagePacker packer, @Nullable Object value) throws IOException {
		if (value == null) {
			packer.packNil();
			return;
		}
		if (value instanceof Boolean booleanValue) {
			packer.packBoolean(booleanValue);
			return;
		}
		if (value instanceof String stringValue) {
			packer.packString(stringValue);
			return;
		}
		if (value instanceof byte[] byteArray) {
			packer.packBinaryHeader(byteArray.length);
			packer.writePayload(byteArray);
			return;
		}
		if (value instanceof BigInteger bigInteger) {
			packer.packBigInteger(bigInteger);
			return;
		}
		if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
			packer.packInt(((Number) value).intValue());
			return;
		}
		if (value instanceof Long longValue) {
			packer.packLong(longValue);
			return;
		}
		if (value instanceof Float floatValue) {
			packer.packFloat(floatValue);
			return;
		}
		if (value instanceof Double doubleValue) {
			packer.packDouble(doubleValue);
			return;
		}
		if (value instanceof BigDecimal bigDecimal) {
			packer.packDouble(bigDecimal.doubleValue());
			return;
		}
		if (value instanceof List<?> listValue) {
			packer.packArrayHeader(listValue.size());
			for (Object element : listValue) {
				packValue(packer, element);
			}
			return;
		}
		if (value instanceof Map<?, ?> mapValue) {
			packer.packMapHeader(mapValue.size());
			for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
				packValue(packer, entry.getKey() == null ? null : entry.getKey().toString());
				packValue(packer, entry.getValue());
			}
			return;
		}
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			packer.packArrayHeader(length);
			for (int i = 0; i < length; i++) {
				packValue(packer, Array.get(value, i));
			}
			return;
		}

		throw new IOException("Unsupported MessagePack value type: " + value.getClass().getName());
	}

	@Nullable private static Object unpackValue(MessageUnpacker unpacker) throws IOException {
		MessageFormat format = unpacker.getNextFormat();
		ValueType valueType = format.getValueType();
		return switch (valueType) {
			case NIL -> {
				unpacker.unpackNil();
				yield null;
			}
			case BOOLEAN -> unpacker.unpackBoolean();
			case INTEGER -> unpackInteger(unpacker);
			case FLOAT -> unpacker.unpackDouble();
			case STRING -> unpacker.unpackString();
			case BINARY -> unpackBinary(unpacker);
			case ARRAY -> unpackArray(unpacker);
			case MAP -> unpackMap(unpacker);
			case EXTENSION -> throw new IOException("MessagePack extension types are not supported.");
		};
	}

	private static Object unpackInteger(MessageUnpacker unpacker) throws IOException {
		BigInteger integerValue = unpacker.unpackBigInteger();
		if (integerValue.bitLength() <= 31) {
			return integerValue.intValue();
		}
		if (integerValue.bitLength() <= 63) {
			return integerValue.longValue();
		}
		return integerValue;
	}

	private static byte[] unpackBinary(MessageUnpacker unpacker) throws IOException {
		int length = unpacker.unpackBinaryHeader();
		byte[] value = new byte[length];
		unpacker.readPayload(value);
		return value;
	}

	private static List<Object> unpackArray(MessageUnpacker unpacker) throws IOException {
		int length = unpacker.unpackArrayHeader();
		List<Object> values = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			values.add(unpackValue(unpacker));
		}
		return values;
	}

	private static Map<String, Object> unpackMap(MessageUnpacker unpacker) throws IOException {
		int length = unpacker.unpackMapHeader();
		Map<String, Object> values = new LinkedHashMap<>(length);
		for (int i = 0; i < length; i++) {
			Object key = unpackValue(unpacker);
			values.put(String.valueOf(key), unpackValue(unpacker));
		}
		return values;
	}

}