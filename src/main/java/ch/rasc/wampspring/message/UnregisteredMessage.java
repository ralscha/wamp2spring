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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [UNREGISTERED, UNREGISTER.Request|id]
 */
public class UnregisteredMessage extends WampMessage {

	static final int CODE = 67;

	private final long requestId;

	public UnregisteredMessage(long requestId) {
		super(CODE);
		this.requestId = requestId;
	}

	public UnregisteredMessage(UnregisterMessage unregisterMessage) {
		this(unregisterMessage.getRequestId());
		setReceiver(unregisterMessage);
	}

	public static UnregisteredMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		return new UnregisteredMessage(request);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
	}

	public long getRequestId() {
		return this.requestId;
	}

	@Override
	public String toString() {
		return "UnregisteredMessage [requestId=" + this.requestId + "]";
	}

}
