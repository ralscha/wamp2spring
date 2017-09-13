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
package ch.rasc.wamp2spring.message;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public class WampRole {
	private final String role;
	private final Set<String> features;

	public WampRole(String role) {
		this.role = role;
		this.features = new HashSet<>();
	}

	String getRole() {
		return this.role;
	}

	Set<String> getFeatures() {
		return this.features;
	}

	public void addFeature(String feature) {
		this.features.add(feature);
	}

	boolean hasFeatures() {
		return !this.features.isEmpty();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.features == null ? 0 : this.features.hashCode());
		result = prime * result + (this.role == null ? 0 : this.role.hashCode());
		return result;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WampRole other = (WampRole) obj;
		if (this.features == null) {
			if (other.features != null) {
				return false;
			}
		}
		else if (!this.features.equals(other.features)) {
			return false;
		}
		if (this.role == null) {
			if (other.role != null) {
				return false;
			}
		}
		else if (!this.role.equals(other.role)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "WampRole [role=" + this.role + ", features=" + this.features + "]";
	}

}
