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
package ch.rasc.wamp2spring.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.message.WampMessageHeader;

/**
 * An {@link ExecutorSubscribableChannel} that preserves per-WAMP-session message
 * ordering. WAMP request IDs are sequential within a session, so subscribers
 * (RpcMessageHandler, PubSubMessageHandler, security/authorization interceptors) rely on
 * observing inbound messages from a single client in the order they were sent.
 *
 * <p>
 * The default {@link ExecutorSubscribableChannel} dispatches each subscriber's
 * {@code handleMessage} on a shared executor: two messages from the same session can be
 * handed to two different threads and then race, leading to anomalies such as a PUBLISH
 * being processed before its preceding SUBSCRIBE, or a CALL arriving at the dealer before
 * the procedure REGISTER it depends on.
 *
 * <p>
 * This implementation queues messages keyed by the
 * {@link WampMessageHeader#WAMP_SESSION_ID} header and runs at most one drain task per
 * session at a time on the wrapped executor. Messages without a session id (e.g.
 * broker-side internally generated traffic, or HELLO/AUTHENTICATE before the session is
 * established) fall through to the default super-class behaviour.
 */
public class OrderedSessionExecutorSubscribableChannel extends ExecutorSubscribableChannel {

	private static final Log log = LogFactory.getLog(OrderedSessionExecutorSubscribableChannel.class);

	private final Executor executor;

	private final ConcurrentHashMap<Object, SessionQueue> queues = new ConcurrentHashMap<>();

	public OrderedSessionExecutorSubscribableChannel(Executor executor) {
		super(executor);
		this.executor = executor;
	}

	@Override
	public boolean sendInternal(Message<?> message, long timeout) {
		Object key = message.getHeaders().get(WampMessageHeader.WAMP_SESSION_ID.name());
		if (key == null) {
			return super.sendInternal(message, timeout);
		}

		SessionQueue queue = this.queues.computeIfAbsent(key, k -> new SessionQueue());
		queue.enqueue(() -> dispatchToSubscribers(message), this.executor);
		return true;
	}

	@EventListener
	public void onWampDisconnect(WampDisconnectEvent event) {
		// Free the per-session queue; any in-flight drain finishes naturally and the
		// next inbound message for a new session id allocates a fresh queue.
		this.queues.remove(event.getWampSessionId());
	}

	private void dispatchToSubscribers(Message<?> message) {
		List<ExecutorChannelInterceptor> executorInterceptors = collectExecutorInterceptors();
		for (MessageHandler handler : getSubscribers()) {
			invokeHandler(handler, message, executorInterceptors);
		}
	}

	private void invokeHandler(MessageHandler handler, Message<?> originalMessage,
			List<ExecutorChannelInterceptor> executorInterceptors) {
		Message<?> messageToHandle = originalMessage;
		Throwable failure = null;
		int interceptorIndex = -1;
		try {
			for (int i = 0; i < executorInterceptors.size(); i++) {
				ExecutorChannelInterceptor interceptor = executorInterceptors.get(i);
				Message<?> nextMessage = interceptor.beforeHandle(messageToHandle, this, handler);
				if (nextMessage == null) {
					return;
				}
				messageToHandle = nextMessage;
				interceptorIndex = i;
			}
			handler.handleMessage(messageToHandle);
		}
		catch (Throwable ex) {
			failure = ex;
		}
		finally {
			triggerAfterMessageHandled(messageToHandle, handler, executorInterceptors, interceptorIndex, failure);
		}

		if (failure != null) {
			if (failure instanceof Error err) {
				throw err;
			}
			if (log.isWarnEnabled()) {
				log.warn("Failed to handle " + originalMessage + " in handler " + handler, failure);
			}
			if (failure instanceof MessagingException me) {
				throw me;
			}
			// Other Throwables (RuntimeException, checked Exception) are logged and
			// swallowed so the drain continues with the next subscriber rather than
			// killing the per-session queue.
		}
	}

	private void triggerAfterMessageHandled(@Nullable Message<?> message, MessageHandler handler,
			List<ExecutorChannelInterceptor> executorInterceptors, int interceptorIndex, @Nullable Throwable ex) {
		if (message == null) {
			return;
		}
		Exception exForCallback;
		if (ex == null) {
			exForCallback = null;
		}
		else if (ex instanceof Exception e) {
			exForCallback = e;
		}
		else {
			exForCallback = new MessagingException("Unhandled error", ex);
		}
		for (int i = interceptorIndex; i >= 0; i--) {
			try {
				executorInterceptors.get(i).afterMessageHandled(message, this, handler, exForCallback);
			}
			catch (Throwable interceptorEx) {
				if (log.isErrorEnabled()) {
					log.error("Exception from afterMessageHandled in " + executorInterceptors.get(i), interceptorEx);
				}
			}
		}
	}

	private List<ExecutorChannelInterceptor> collectExecutorInterceptors() {
		List<ChannelInterceptor> all = getInterceptors();
		if (all.isEmpty()) {
			return List.of();
		}
		List<ExecutorChannelInterceptor> result = new ArrayList<>(all.size());
		for (ChannelInterceptor interceptor : all) {
			if (interceptor instanceof ExecutorChannelInterceptor exec) {
				result.add(exec);
			}
		}
		return result;
	}

	/**
	 * Holds the FIFO of pending dispatches for a single session and ensures only one
	 * drain runs at a time on the backing executor.
	 */
	private static final class SessionQueue {

		private final Deque<Runnable> tasks = new ArrayDeque<>();

		private boolean running = false;

		void enqueue(Runnable task, Executor executor) {
			boolean shouldStart;
			synchronized (this) {
				this.tasks.add(task);
				shouldStart = !this.running;
				if (shouldStart) {
					this.running = true;
				}
			}
			if (shouldStart) {
				try {
					executor.execute(this::drain);
				}
				catch (RejectedExecutionException ex) {
					// Match ExecutorSubscribableChannel's caller-thread fallback. The
					// queue must still drain when the executor is saturated or stopping.
					drain();
				}
			}
		}

		private void drain() {
			while (true) {
				Runnable next;
				synchronized (this) {
					next = this.tasks.pollFirst();
					if (next == null) {
						this.running = false;
						return;
					}
				}
				try {
					next.run();
				}
				catch (Throwable ex) {
					if (log.isErrorEnabled()) {
						log.error("Unhandled error draining session queue", ex);
					}
				}
			}
		}

	}

}
