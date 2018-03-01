/**
 * Copyright 2017-2018 the original author or authors.
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
package ch.rasc.wamp2spring.servlet;

import java.util.List;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * A {@link HandshakeHandler} implementation that prefers a binary data format in the
 * order MessagePack, Smile and CBOR.
 *
 * You can configure this {@link HandshakeHandler} by extending the
 * {@link WampServletConfiguration} and overwriting the
 * {@link WampServletConfiguration#getHandshakeHandler()} method.
 *
 * <pre class="code">
 * &#64;Configuration
 * public class Application extends WampConfiguration {
 *
 * 	&#64;Override
 * 	protected HandshakeHandler getHandshakeHandler() {
 * 		return new PreferBinaryHandshakeHandler();
 * 	}
 *
 * }
 * </pre>
 */
public class PreferBinaryHandshakeHandler extends DefaultHandshakeHandler {

	@Override
	protected String selectProtocol(List<String> requestedProtocols,
			WebSocketHandler webSocketHandler) {

		if (requestedProtocols.contains(WampSubProtocolHandler.MSGPACK_PROTOCOL)) {
			return WampSubProtocolHandler.MSGPACK_PROTOCOL;
		}

		if (requestedProtocols.contains(WampSubProtocolHandler.SMILE_PROTOCOL)) {
			return WampSubProtocolHandler.SMILE_PROTOCOL;
		}

		if (requestedProtocols.contains(WampSubProtocolHandler.CBOR_PROTOCOL)) {
			return WampSubProtocolHandler.CBOR_PROTOCOL;
		}

		return super.selectProtocol(requestedProtocols, webSocketHandler);
	}

}
