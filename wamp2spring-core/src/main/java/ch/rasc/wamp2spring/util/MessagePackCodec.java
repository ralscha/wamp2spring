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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePackException;
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

	private static final int MAX_NESTING_DEPTH = 256;

	private MessagePackCodec() {
		// utility class
	}

	public static byte[] toJson(byte[] messagePack, ObjectMapper jsonObjectMapper) throws IOException {
		return jsonObjectMapper.writeValueAsBytes(unpackMessage(messagePack));
	}

	public static WampMessage deserializeWampMessage(byte[] messagePack, ObjectMapper jsonObjectMapper)
			throws IOException {
		JsonNode root = jsonObjectMapper.valueToTree(unpackMessage(messagePack));
		try (JsonParser parser = jsonObjectMapper.treeAsTokens(root)) {
			return WampMessage.deserialize(parser);
		}
	}

	@Nullable private static Object unpackMessage(byte[] messagePack) throws IOException {
		try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(messagePack)) {
			Object value = unpackValue(unpacker, messagePack.length, 0);
			if (unpacker.hasNext()) {
				throw new IOException("A MessagePack frame must contain exactly one value.");
			}
			return value;
		}
		catch (MessagePackException ex) {
			throw new IOException("Invalid MessagePack payload.", ex);
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

	@Nullable private static Object unpackValue(MessageUnpacker unpacker, int frameLength, int depth) throws IOException {
		MessageFormat format = unpacker.getNextFormat();
		ValueType valueType = format.getValueType();
		if (depth >= MAX_NESTING_DEPTH && (valueType == ValueType.ARRAY || valueType == ValueType.MAP)) {
			throw new IOException("MessagePack nesting exceeds " + MAX_NESTING_DEPTH + " containers.");
		}
		return switch (valueType) {
			case NIL -> {
				unpacker.unpackNil();
				yield null;
			}
			case BOOLEAN -> unpacker.unpackBoolean();
			case INTEGER -> unpackInteger(unpacker);
			case FLOAT -> unpacker.unpackDouble();
			case STRING -> unpackString(unpacker, frameLength);
			case BINARY -> unpackBinary(unpacker, frameLength);
			case ARRAY -> unpackArray(unpacker, frameLength, depth + 1);
			case MAP -> unpackMap(unpacker, frameLength, depth + 1);
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

	private static void requireRemaining(MessageUnpacker unpacker, int frameLength, long minimumBytes)
			throws IOException {
		if (minimumBytes < 0 || minimumBytes > frameLength - unpacker.getTotalReadBytes()) {
			throw new IOException("MessagePack value length exceeds the remaining frame payload.");
		}
	}

	private static String unpackString(MessageUnpacker unpacker, int frameLength) throws IOException {
		int length = unpacker.unpackRawStringHeader();
		requireRemaining(unpacker, frameLength, length);
		return new String(unpacker.readPayload(length), StandardCharsets.UTF_8);
	}

	private static byte[] unpackBinary(MessageUnpacker unpacker, int frameLength) throws IOException {
		int length = unpacker.unpackBinaryHeader();
		requireRemaining(unpacker, frameLength, length);
		return unpacker.readPayload(length);
	}

	private static List<Object> unpackArray(MessageUnpacker unpacker, int frameLength, int depth) throws IOException {
		int length = unpacker.unpackArrayHeader();
		requireRemaining(unpacker, frameLength, length);
		List<Object> values = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			values.add(unpackValue(unpacker, frameLength, depth));
		}
		return values;
	}

	private static Map<String, Object> unpackMap(MessageUnpacker unpacker, int frameLength, int depth)
			throws IOException {
		int length = unpacker.unpackMapHeader();
		requireRemaining(unpacker, frameLength, length * 2L);
		Map<String, Object> values = new LinkedHashMap<>(length);
		for (int i = 0; i < length; i++) {
			if (unpacker.getNextFormat().getValueType() != ValueType.STRING) {
				throw new IOException("WAMP dictionary keys must be strings.");
			}
			String key = unpackString(unpacker, frameLength);
			values.put(key, unpackValue(unpacker, frameLength, depth));
		}
		return values;
	}

}