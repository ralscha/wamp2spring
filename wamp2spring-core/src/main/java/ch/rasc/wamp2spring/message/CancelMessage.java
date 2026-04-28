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

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * [CANCEL, CALL.Request|id, Options|dict]
 */
public class CancelMessage extends WampMessage {

	public static final int CODE = 49;

	public static final String MODE_SKIP = "skip";

	public static final String MODE_KILL = "kill";

	public static final String MODE_KILLNOWAIT = "killnowait";

	private final long requestId;

	@Nullable private final String mode;

	public CancelMessage(long requestId) {
		this(requestId, null);
	}

	public CancelMessage(long requestId, @Nullable String mode) {
		super(CODE);
		this.requestId = requestId;
		this.mode = mode;
	}

	public static CancelMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		String mode = null;
		if (options != null) {
			mode = (String) options.get("mode");
		}

		return new CancelMessage(request, mode);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.mode != null) {
			generator.writeStringProperty("mode", this.mode);
		}
		generator.writeEndObject();
	}

	public long getRequestId() {
		return this.requestId;
	}

	@Nullable public String getMode() {
		return this.mode;
	}

	public String getModeOrDefault() {
		return this.mode != null ? this.mode : MODE_SKIP;
	}

	@Override
	public String toString() {
		return "CancelMessage [requestId=" + this.requestId + ", mode=" + this.mode + "]";
	}

}