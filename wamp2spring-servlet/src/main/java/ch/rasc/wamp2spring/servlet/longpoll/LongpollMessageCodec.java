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
package ch.rasc.wamp2spring.servlet.longpoll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.http.MediaType;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;
import ch.rasc.wamp2spring.util.MessagePackCodec;

final class LongpollMessageCodec {

	private LongpollMessageCodec() {
	}

	static WampMessage deserialize(String protocol, ObjectMapper objectMapper, byte[] payload) throws IOException {
		if (WampSubProtocolHandler.MSGPACK_PROTOCOL.equals(protocol)) {
			return WampMessage.deserialize(objectMapper, MessagePackCodec.toJson(payload, objectMapper));
		}
		return WampMessage.deserialize(objectMapper, payload);
	}

	static byte[] serialize(String protocol, ObjectMapper objectMapper, WampMessage wampMessage) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = objectMapper.createGenerator(bos)) {
			generator.writeStartArray();
			wampMessage.serialize(generator);
			generator.writeEndArray();
			generator.close();
			byte[] payload = bos.toByteArray();
			if (WampSubProtocolHandler.MSGPACK_PROTOCOL.equals(protocol)) {
				return MessagePackCodec.fromJson(payload, objectMapper);
			}
			return payload;
		}
	}

	static MediaType mediaType(String protocol) {
		return WampSubProtocolHandler.JSON_PROTOCOL.equals(protocol) ? MediaType.APPLICATION_JSON
				: MediaType.APPLICATION_OCTET_STREAM;
	}

}