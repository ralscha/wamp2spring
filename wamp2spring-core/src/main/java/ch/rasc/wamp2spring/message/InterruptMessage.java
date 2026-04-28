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
package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [INTERRUPT, INVOCATION.Request|id, Options|dict]
 */
public class InterruptMessage extends WampMessage {

	public static final int CODE = 69;

	private final long requestId;

	@Nullable private final String mode;

	public InterruptMessage(long requestId, @Nullable String mode) {
		super(CODE);
		this.requestId = requestId;
		this.mode = mode;
	}

	public InterruptMessage(long requestId, @Nullable String mode, @Nullable String receiverWebSocketSessionId) {
		this(requestId, mode);
		if (receiverWebSocketSessionId != null) {
			setReceiverWebSocketSessionId(receiverWebSocketSessionId);
		}
	}

	public static InterruptMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		String mode = null;
		if (options != null) {
			mode = (String) options.get("mode");
		}

		return new InterruptMessage(request, mode);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.mode != null) {
			generator.writeStringField("mode", this.mode);
		}
		generator.writeEndObject();
	}

	public long getRequestId() {
		return this.requestId;
	}

	@Nullable public String getMode() {
		return this.mode;
	}

	@Override
	public String toString() {
		return "InterruptMessage [requestId=" + this.requestId + ", mode=" + this.mode + "]";
	}

}