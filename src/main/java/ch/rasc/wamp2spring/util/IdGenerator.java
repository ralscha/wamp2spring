/**
 * Copyright 2017-2017 the original author or authors.
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

package ch.rasc.wamp2spring.util;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

public class IdGenerator {

	public static final long MIN = 1L;
	public static final long MAX = 9007199254740992L;

	/**
	 * global scope
	 */
	public static long newRandomId(@Nullable Set<Long> existingIds) {
		while (true) {
			long candidateId = ThreadLocalRandom.current().nextLong();
			if (MIN <= candidateId && candidateId <= MAX) {
				if (existingIds == null) {
					return candidateId;
				}
				if (!existingIds.contains(candidateId)) {
					return candidateId;
				}
			}
		}
	}

	/**
	 * session scope and router scope ids
	 */
	public static long newLinearId(AtomicLong longValue) {
		long candiateId = longValue.incrementAndGet();
		if (candiateId > IdGenerator.MAX) {
			longValue.set(1L);
			candiateId = longValue.longValue();
		}
		return candiateId;
	}

}