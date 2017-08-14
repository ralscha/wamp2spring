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
/**
 * Base class of the WampMessages
 */
import java.security.Principal;
import java.util.Map;

import javax.annotation.Nullable;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public abstract class WampMessage implements Message<Object> {

	private final static Object EMPTY_OBJECT = new Object();

	private final MutableMessageHeaders messageHeaders = new MutableMessageHeaders();

	private final int code;

	WampMessage(int code) {
		this.code = code;
		setHeader(WampMessageHeader.WAMP_MESSAGE_CODE, code);
	}

	public int getCode() {
		return this.code;
	}

	public void setHeader(WampMessageHeader header, Object value) {
		this.messageHeaders.getRawHeaders().put(header.name(), value);
	}

	@SuppressWarnings("unchecked")
	public <T> T getHeader(WampMessageHeader header) {
		return (T) this.messageHeaders.get(header.name());
	}

	public String getWebSocketSessionId() {
		return getHeader(WampMessageHeader.WEBSOCKET_SESSION_ID);
	}

	public Principal getPrincipal() {
		return getHeader(WampMessageHeader.PRINCIPAL);
	}

	public Long getWampSessionId() {
		return getHeader(WampMessageHeader.WAMP_SESSION_ID);
	}

	public void setWebSocketSession(WebSocketSession webSocketSession) {
		setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSession.getId());
		setHeader(WampMessageHeader.PRINCIPAL, webSocketSession.getPrincipal());
		setHeader(WampMessageHeader.WAMP_SESSION_ID, webSocketSession.getAttributes()
				.get(WampMessageHeader.WAMP_SESSION_ID.name()));
	}

	protected void setReceiver(WampMessage message) {
		setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID,
				message.getWebSocketSessionId());
		setHeader(WampMessageHeader.PRINCIPAL, message.getPrincipal());
		setHeader(WampMessageHeader.WAMP_SESSION_ID, message.getWampSessionId());
	}

	protected void setReceiverWebSocketSessionId(String receiverWebSocketSessionId) {
		setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, receiverWebSocketSessionId);
	}

	@Override
	public Object getPayload() {
		return EMPTY_OBJECT;
	}

	@Override
	public MessageHeaders getHeaders() {
		return this.messageHeaders;
	}

	public abstract void serialize(JsonGenerator generator) throws IOException;

	@SuppressWarnings("unchecked")
	@Nullable
	public static <T extends WampMessage> T deserialize(JsonFactory jsonFactory,
			byte[] json) throws JsonParseException, IOException {

		try (JsonParser jp = jsonFactory.createParser(json)) {
			if (jp.nextToken() != JsonToken.START_ARRAY) {
				throw new IOException("Not a JSON array");
			}
			if (jp.nextToken() != JsonToken.VALUE_NUMBER_INT) {
				throw new IOException("Wrong message format");
			}

			int code = jp.getValueAsInt();

			switch (code) {
			case HelloMessage.CODE:
				return (T) HelloMessage.deserialize(jp);
			case WelcomeMessage.CODE:
				return (T) WelcomeMessage.deserialize(jp);
			case AbortMessage.CODE:
				return (T) AbortMessage.deserialize(jp);
			case GoodbyeMessage.CODE:
				return (T) GoodbyeMessage.deserialize(jp);
			case ErrorMessage.CODE:
				return (T) ErrorMessage.deserialize(jp);
			case PublishMessage.CODE:
				return (T) PublishMessage.deserialize(jp);
			case PublishedMessage.CODE:
				return (T) PublishedMessage.deserialize(jp);
			case SubscribeMessage.CODE:
				return (T) SubscribeMessage.deserialize(jp);
			case SubscribedMessage.CODE:
				return (T) SubscribedMessage.deserialize(jp);
			case UnsubscribeMessage.CODE:
				return (T) UnsubscribeMessage.deserialize(jp);
			case UnsubscribedMessage.CODE:
				return (T) UnsubscribedMessage.deserialize(jp);
			case EventMessage.CODE:
				return (T) EventMessage.deserialize(jp);
			case CallMessage.CODE:
				return (T) CallMessage.deserialize(jp);
			case ResultMessage.CODE:
				return (T) ResultMessage.deserialize(jp);
			case RegisterMessage.CODE:
				return (T) RegisterMessage.deserialize(jp);
			case RegisteredMessage.CODE:
				return (T) RegisteredMessage.deserialize(jp);
			case UnregisterMessage.CODE:
				return (T) UnregisterMessage.deserialize(jp);
			case UnregisteredMessage.CODE:
				return (T) UnregisteredMessage.deserialize(jp);
			case YieldMessage.CODE:
				return (T) YieldMessage.deserialize(jp);
			case InvocationMessage.CODE:
				return (T) InvocationMessage.deserialize(jp);
			default:
				return null;
			}

		}

	}

	@SuppressWarnings("serial")
	private static class MutableMessageHeaders extends MessageHeaders {

		public MutableMessageHeaders() {
			super(null, MessageHeaders.ID_VALUE_NONE, -1L);
		}

		@Override
		public Map<String, Object> getRawHeaders() {
			return super.getRawHeaders();
		}
	}

}
