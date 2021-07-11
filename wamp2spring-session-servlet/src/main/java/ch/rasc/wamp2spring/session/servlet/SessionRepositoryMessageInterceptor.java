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
package ch.rasc.wamp2spring.session.servlet;

import java.time.Instant;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.WelcomeMessage;

public final class SessionRepositoryMessageInterceptor<S extends Session>
		implements ChannelInterceptor, HandshakeInterceptor {

	private static final String SPRING_SESSION_ID_ATTR_NAME = "SPRING.SESSION.ID";

	private final SessionRepository<S> sessionRepository;

	public SessionRepositoryMessageInterceptor(SessionRepository<S> sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (messageMatches(message)) {
			String sessionId = (String) message.getHeaders()
					.get(SPRING_SESSION_ID_ATTR_NAME);
			if (sessionId != null) {
				S session = this.sessionRepository.findById(sessionId);
				if (session != null) {
					// update the last accessed time
					session.setLastAccessedTime(Instant.now());
					this.sessionRepository.save(session);
				}
			}
		}
		return message;
	}

	private static boolean messageMatches(Message<?> message) {
		return !(message instanceof AbortMessage) && !(message instanceof ErrorMessage)
				&& !(message instanceof GoodbyeMessage)
				&& !(message instanceof HelloMessage)
				&& !(message instanceof WelcomeMessage);
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
		if (request instanceof ServletServerHttpRequest) {
			ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
			HttpSession session = servletRequest.getServletRequest().getSession(false);
			if (session != null) {
				attributes.put(SPRING_SESSION_ID_ATTR_NAME, session.getId());
			}
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
		// nothing here
	}

}
