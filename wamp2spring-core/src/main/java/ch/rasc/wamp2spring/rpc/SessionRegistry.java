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
package ch.rasc.wamp2spring.rpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jspecify.annotations.Nullable;

public class SessionRegistry {

	private final ConcurrentMap<Long, SessionDetail> sessions = new ConcurrentHashMap<>();

	public void add(SessionDetail detail) {
		this.sessions.put(detail.getSessionId(), detail);
	}

	@Nullable public SessionDetail remove(long sessionId) {
		return this.sessions.remove(sessionId);
	}

	@Nullable public SessionDetail get(long sessionId) {
		return this.sessions.get(sessionId);
	}

	public int count(@Nullable Collection<String> authRoles) {
		return filteredSessions(authRoles).size();
	}

	public List<Long> list(@Nullable Collection<String> authRoles) {
		List<Long> result = new ArrayList<>();
		for (SessionDetail detail : filteredSessions(authRoles)) {
			result.add(detail.getSessionId());
		}
		return result;
	}

	public List<SessionDetail> listDetails() {
		List<SessionDetail> result = new ArrayList<>();
		for (SessionDetail detail : this.sessions.values()) {
			result.add(detail);
		}
		result.sort(Comparator.comparingLong(SessionDetail::getSessionId));
		return result;
	}

	public List<SessionDetail> findByAuthId(String authId) {
		List<SessionDetail> result = new ArrayList<>();
		for (SessionDetail detail : this.sessions.values()) {
			if (authId.equals(detail.getAuthId())) {
				result.add(detail);
			}
		}
		result.sort(Comparator.comparingLong(SessionDetail::getSessionId));
		return result;
	}

	public List<SessionDetail> findByAuthRole(String authRole) {
		List<SessionDetail> result = new ArrayList<>();
		for (SessionDetail detail : this.sessions.values()) {
			if (authRole.equals(detail.getAuthRole())) {
				result.add(detail);
			}
		}
		result.sort(Comparator.comparingLong(SessionDetail::getSessionId));
		return result;
	}

	private List<SessionDetail> filteredSessions(@Nullable Collection<String> authRoles) {
		List<SessionDetail> result = new ArrayList<>();
		for (SessionDetail detail : this.sessions.values()) {
			if (authRoles == null || authRoles.isEmpty()) {
				result.add(detail);
			}
			else if (detail.getAuthRole() != null && authRoles.contains(detail.getAuthRole())) {
				result.add(detail);
			}
		}
		result.sort(Comparator.comparingLong(SessionDetail::getSessionId));
		return result;
	}

}