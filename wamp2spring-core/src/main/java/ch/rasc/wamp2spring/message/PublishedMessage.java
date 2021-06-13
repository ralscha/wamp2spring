/**
 * Copyright 2017-2021 the original author or authors.
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

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * [PUBLISHED, PUBLISH.Request|id, Publication|id]
 */
public class PublishedMessage extends WampMessage {

	static final int CODE = 17;

	private final long requestId;

	private final long publicationId;

	public PublishedMessage(long requestId, long publicationId) {
		super(CODE);
		this.requestId = requestId;
		this.publicationId = publicationId;
	}

	public PublishedMessage(PublishMessage publishMessage, long publicationId) {
		this(publishMessage.getRequestId(), publicationId);
		setReceiver(publishMessage);
	}

	public static PublishedMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long subscription = jp.getLongValue();

		return new PublishedMessage(request, subscription);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeNumber(this.publicationId);
	}

	public long getRequestId() {
		return this.requestId;
	}

	public long getPublicationId() {
		return this.publicationId;
	}

	@Override
	public String toString() {
		return "PublishedMessage [requestId=" + this.requestId + ", publicationId="
				+ this.publicationId + "]";
	}

}
