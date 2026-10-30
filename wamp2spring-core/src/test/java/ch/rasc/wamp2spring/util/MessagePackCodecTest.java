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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.msgpack.core.MessagePack;

import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.message.SubscribeMessage;

public class MessagePackCodecTest {

	@Test
	public void deserializesWampMessageWithoutIntermediateJsonBytes() throws Exception {
		String json = "[32,123,{\"match\":\"prefix\",\"get_retained\":true},\"com.myapp\"]";
		byte[] msgpack = MessagePackCodec.fromJson(json.getBytes(StandardCharsets.UTF_8),
				WampJson.createJsonObjectMapper());

		SubscribeMessage subscribeMessage = (SubscribeMessage) MessagePackCodec.deserializeWampMessage(msgpack,
				WampJson.createJsonObjectMapper());

		assertThat(subscribeMessage.getRequestId()).isEqualTo(123L);
		assertThat(subscribeMessage.getTopic()).isEqualTo("com.myapp");
		assertThat(subscribeMessage.getMatchPolicy().getExternalValue()).isEqualTo("prefix");
		assertThat(subscribeMessage.isGetRetained()).isTrue();
	}

	@Test
	public void rejectsImpossibleLengthsBeforeAllocatingPayloads() {
		for (int type : new int[] { 0xc6, 0xdb, 0xdd, 0xdf }) {
			byte[] payload = { (byte) type, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff };
			assertThatIOException()
				.isThrownBy(() -> MessagePackCodec.toJson(payload, WampJson.createJsonObjectMapper()))
				.withMessageContaining("remaining frame payload");
		}
	}

	@Test
	public void rejectsExcessiveNestingWithoutOverflowingTheStack() {
		byte[] payload = new byte[258];
		Arrays.fill(payload, (byte) 0x91);
		payload[payload.length - 1] = (byte) 0xc0;
		assertThatIOException().isThrownBy(() -> MessagePackCodec.toJson(payload, WampJson.createJsonObjectMapper()))
			.withMessageContaining("nesting");
	}

	@Test
	public void rejectsTrailingValuesInUnbatchedFrames() throws Exception {
		var mapper = WampJson.createJsonObjectMapper();
		byte[] message = MessagePackCodec.fromJson("[32,123,{},\"com.myapp\"]".getBytes(StandardCharsets.UTF_8),
				mapper);
		byte[] trailing = Arrays.copyOf(message, message.length + 1);
		assertThatIOException().isThrownBy(() -> MessagePackCodec.toJson(trailing, mapper))
			.withMessageContaining("exactly one value");
		assertThatIOException().isThrownBy(() -> MessagePackCodec.deserializeWampMessage(trailing, mapper))
			.withMessageContaining("exactly one value");
	}

	@Test
	public void rejectsNonStringDictionaryKeys() throws Exception {
		try (var packer = MessagePack.newDefaultBufferPacker()) {
			packer.packMapHeader(1).packInt(123).packString("value");
			byte[] payload = packer.toByteArray();
			assertThatIOException()
				.isThrownBy(() -> MessagePackCodec.toJson(payload, WampJson.createJsonObjectMapper()))
				.withMessageContaining("dictionary keys");
		}
	}

}