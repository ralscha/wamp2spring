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
package ch.rasc.wampspring.event;

import java.security.Principal;

import org.springframework.lang.Nullable;

public class WampEvent {
	@Nullable
	private final Principal principal;

	private final Long wampSessionId;

	private final String webSocketSessionId;

	public WampEvent(Long wampSessionId, String webSocketSessionId,
			@Nullable Principal principal) {
		this.wampSessionId = wampSessionId;
		this.principal = principal;
		this.webSocketSessionId = webSocketSessionId;
	}

	public String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	@Nullable
	public Principal getPrincipal() {
		return this.principal;
	}

	public Long getWampSessionId() {
		return this.wampSessionId;
	}

}
