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

import java.io.ByteArrayOutputStream;

/**
 * Thread-local pool of {@link ByteArrayOutputStream} instances used by the WebSocket
 * transports for outbound WAMP message serialization. Each thread keeps a single buffer
 * that is reset between writes, eliminating per-message allocations on the hot path.
 */
public final class PooledByteArrayOutputStream {

	/**
	 * Initial capacity for newly created buffers. Most WAMP messages are small; capacity
	 * grows automatically when needed.
	 */
	private static final int INITIAL_CAPACITY = 512;

	/**
	 * Buffers larger than this threshold (in bytes) are not retained in the pool after
	 * use. This bounds the per-thread memory footprint and avoids holding onto rarely
	 * needed large allocations.
	 */
	private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

	private static final ThreadLocal<ByteArrayOutputStream> BUFFER = ThreadLocal
		.withInitial(() -> new ByteArrayOutputStream(INITIAL_CAPACITY));

	private PooledByteArrayOutputStream() {
		// utility class
	}

	/**
	 * Acquire the calling thread's buffer, ready for writing (already reset). Always pair
	 * each call with {@link #release(ByteArrayOutputStream)} in a try/finally.
	 */
	public static ByteArrayOutputStream acquire() {
		ByteArrayOutputStream bos = BUFFER.get();
		bos.reset();
		return bos;
	}

	/**
	 * Release the buffer back to the pool. If the buffer has grown past
	 * {@link #MAX_RETAINED_CAPACITY} it is dropped so the next acquire allocates a fresh,
	 * smaller one.
	 */
	public static void release(ByteArrayOutputStream bos) {
		// ByteArrayOutputStream exposes neither capacity nor a way to shrink; the size
		// of the most recent write is the best proxy we have for "how big did this grow".
		if (bos.size() > MAX_RETAINED_CAPACITY) {
			BUFFER.remove();
		}
	}

}
