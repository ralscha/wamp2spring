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

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.servlet.WampSessionSupport;

@RestController
@RequestMapping("/wamp")
public class WampLongpollController {

	private final LongpollTransportRegistry transportRegistry;

	private final org.springframework.messaging.MessageChannel clientInboundChannel;

	private final WampSessionSupport sessionSupport;

	public WampLongpollController(LongpollTransportRegistry transportRegistry,
			org.springframework.messaging.MessageChannel clientInboundChannel, WampSessionSupport sessionSupport) {
		this.transportRegistry = transportRegistry;
		this.clientInboundChannel = clientInboundChannel;
		this.sessionSupport = sessionSupport;
	}

	@PostMapping(path = "/open", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> open(@RequestBody Map<String, Object> requestBody, @Nullable Principal principal) {
		LongpollTransport transport = this.transportRegistry.create(selectProtocol(requestBody), principal);
		return Map.of("transport", transport.getTransportId(), "protocol", transport.getProtocol());
	}

	private String selectProtocol(Map<String, Object> requestBody) {
		if (requestBody.containsKey("protocols")) {
			Object value = requestBody.get("protocols");
			if (!(value instanceof List<?> protocols) || protocols.isEmpty()
					|| protocols.stream().anyMatch(protocol -> !(protocol instanceof String text) || text.isBlank())) {
				throw new IllegalArgumentException("protocols must be a nonempty list of protocol names");
			}
			for (Object protocol : protocols) {
				if (this.transportRegistry.supportsProtocol((String) protocol)) {
					return (String) protocol;
				}
			}
			throw new IllegalArgumentException("No supported longpoll protocol was offered");
		}
		// Preserve the original single-protocol request format.
		if (requestBody.get("protocol") instanceof String protocol && !protocol.isBlank()) {
			return protocol;
		}
		throw new IllegalArgumentException("protocols is required");
	}

	@PostMapping(path = "/{transportId}/close")
	public ResponseEntity<Void> close(@PathVariable String transportId) {
		LongpollTransport removedTransport = this.transportRegistry.remove(transportId);
		if (removedTransport == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/{transportId}/send")
	public ResponseEntity<Void> send(@PathVariable String transportId, @RequestBody byte[] payload) throws IOException {
		LongpollTransport transport = this.transportRegistry.get(transportId);
		if (transport == null || transport.getCloseAfterDrain().get()) {
			throw new TransportNotFoundException();
		}

		WampMessage wampMessage;
		try {
			wampMessage = LongpollMessageCodec.deserialize(transport.getProtocol(), transport.getObjectMapper(),
					payload);
		}
		catch (IOException | tools.jackson.core.JacksonException ex) {
			throw new IllegalArgumentException("Invalid WAMP payload", ex);
		}
		if (wampMessage == null) {
			throw new IllegalArgumentException("payload did not contain a WAMP message");
		}
		this.sessionSupport.populateHeaders(transport.getTransportId(), transport.getPrincipal(),
				transport.getAttributes(), wampMessage);

		if (wampMessage instanceof HelloMessage helloMessage) {
			handleSessionResponse(transport, this.sessionSupport.handleHelloMessage(transport.getTransportId(),
					transport.getPrincipal(), transport.getAttributes(), helloMessage));
			return ResponseEntity.noContent().build();
		}

		if (wampMessage instanceof AuthenticateMessage authenticateMessage) {
			handleSessionResponse(transport, this.sessionSupport.handleAuthenticateMessage(transport.getTransportId(),
					transport.getPrincipal(), transport.getAttributes(), authenticateMessage));
			return ResponseEntity.noContent().build();
		}

		if (wampMessage instanceof AbortMessage) {
			this.transportRegistry.remove(transportId);
			return ResponseEntity.noContent().build();
		}

		if (wampMessage instanceof GoodbyeMessage) {
			this.transportRegistry.queue(transport, new GoodbyeMessage(WampError.GOODBYE_AND_OUT), true);
			return ResponseEntity.noContent().build();
		}

		Long wampSessionId = wampMessage.getWampSessionId();
		if (wampSessionId == null) {
			this.transportRegistry.queue(transport,
					new AbortMessage(WampError.PROTOCOL_VIOLATION,
							transport.getAttributes().get(WampSessionSupport.WAMP_PENDING_AUTH) != null
									? "Expected AUTHENTICATE while authentication was in progress."
									: "Received message before session was established."),
					true);
			return ResponseEntity.noContent().build();
		}

		this.clientInboundChannel.send(wampMessage);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/{transportId}/receive")
	public DeferredResult<ResponseEntity<byte[]>> receive(@PathVariable String transportId) {
		LongpollTransport transport = this.transportRegistry.get(transportId);
		if (transport == null) {
			throw new TransportNotFoundException();
		}
		return this.transportRegistry.receive(transport);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(Map.of("error", Objects.requireNonNullElse(exception.getMessage(), "Invalid request")));
	}

	@ExceptionHandler(TransportNotFoundException.class)
	public ResponseEntity<Void> handleTransportNotFound(RuntimeException exception) {
		return ResponseEntity.notFound().build();
	}

	private void handleSessionResponse(LongpollTransport transport,
			WampSessionSupport.SessionResponse sessionResponse) {
		this.transportRegistry.queue(transport, sessionResponse.message(), sessionResponse.closeTransport());
	}

	private static final class TransportNotFoundException extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

}
