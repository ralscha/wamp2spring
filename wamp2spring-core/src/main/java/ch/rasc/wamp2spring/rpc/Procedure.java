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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;

public class Procedure {

	private final String procedure;

	private final String webSocketSessionId;

	@Nullable private final Long wampSessionId;

	private final DestinationMatch procedureMatch;

	private final MatchPolicy matchPolicy;

	private final boolean discloseCaller;

	private final boolean callCancelingSupported;

	private final boolean callTimeoutSupported;

	private final boolean progressiveCallResultsSupported;

	private final boolean progressiveCallInvocationsSupported;

	private final boolean registrationRevocationSupported;

	private final boolean callRerouteSupported;

	private final InvocationPolicy invocationPolicy;

	private final long registrationId;

	private final Set<Long> pendingInvocations;

	private final int prefixComponentCount;

	private final List<Integer> wildcardSpecificity;

	public Procedure(RegisterMessage registerMessage, long registrationId,
			boolean isDealerCallerIdentificationFeatureEnabled) {
		this.procedure = registerMessage.getProcedure();
		this.webSocketSessionId = Objects.requireNonNull(registerMessage.getWebSocketSessionId());
		this.wampSessionId = registerMessage.getWampSessionId();
		this.matchPolicy = registerMessage.getMatchPolicy();
		this.procedureMatch = new DestinationMatch(this.procedure, this.matchPolicy);
		if (isDealerCallerIdentificationFeatureEnabled) {
			this.discloseCaller = registerMessage.isDiscloseCaller();
		}
		else {
			this.discloseCaller = false;
		}
		this.callCancelingSupported = supportsCallCanceling(registerMessage.getPeerRoles());
		this.callTimeoutSupported = supportsCallTimeout(registerMessage.getPeerRoles());
		this.progressiveCallResultsSupported = supportsProgressiveCallResults(registerMessage.getPeerRoles(),
				this.callCancelingSupported);
		this.progressiveCallInvocationsSupported = supportsProgressiveCallInvocations(registerMessage.getPeerRoles(),
				this.callCancelingSupported);
		this.registrationRevocationSupported = supportsRegistrationRevocation(registerMessage.getPeerRoles());
		this.callRerouteSupported = supportsCallReroute(registerMessage.getPeerRoles());
		this.invocationPolicy = registerMessage.getInvokePolicy();
		this.registrationId = registrationId;
		this.pendingInvocations = ConcurrentHashMap.newKeySet();
		this.prefixComponentCount = componentCount(this.procedure);
		this.wildcardSpecificity = wildcardSpecificity(this.procedure);
	}

	private static boolean supportsCallCanceling(@Nullable List<WampRole> roles) {
		return supportsFeature(roles, Feature.DEALER_CALL_CANCELING);
	}

	private static boolean supportsCallTimeout(@Nullable List<WampRole> roles) {
		return supportsFeature(roles, Feature.DEALER_CALL_TIMEOUT);
	}

	private static boolean supportsRegistrationRevocation(@Nullable List<WampRole> roles) {
		return supportsFeature(roles, Feature.DEALER_REGISTRATION_REVOCATION);
	}

	private static boolean supportsCallReroute(@Nullable List<WampRole> roles) {
		return supportsFeature(roles, Feature.DEALER_CALL_REROUTE);
	}

	private static boolean supportsProgressiveCallResults(@Nullable List<WampRole> roles,
			boolean callCancelingSupported) {
		return callCancelingSupported && supportsFeature(roles, Feature.DEALER_PROGRESSIVE_CALL_RESULTS);
	}

	private static boolean supportsProgressiveCallInvocations(@Nullable List<WampRole> roles,
			boolean callCancelingSupported) {
		return callCancelingSupported && supportsFeature(roles, Feature.DEALER_PROGRESSIVE_CALL_INVOCATIONS);
	}

	private static boolean supportsFeature(@Nullable List<WampRole> roles, Feature feature) {
		if (roles == null) {
			return false;
		}

		for (WampRole role : roles) {
			if ("callee".equals(role.getRole()) && role.hasFeature(feature.getExternalValue())) {
				return true;
			}
		}

		return false;
	}

	public String getProcedure() {
		return this.procedure;
	}

	public String getWebSocketSessionId() {
		return this.webSocketSessionId;
	}

	@Nullable public Long getWampSessionId() {
		return this.wampSessionId;
	}

	public DestinationMatch getProcedureMatch() {
		return this.procedureMatch;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	public void addPendingInvocation(long requestId) {
		this.pendingInvocations.add(requestId);
	}

	public void removePendingInvocation(long requestId) {
		this.pendingInvocations.remove(requestId);
	}

	public Set<Long> getPendingInvocations() {
		return this.pendingInvocations;
	}

	public boolean isDiscloseCaller() {
		return this.discloseCaller;
	}

	public boolean isCallCancelingSupported() {
		return this.callCancelingSupported;
	}

	public boolean isCallTimeoutSupported() {
		return this.callTimeoutSupported;
	}

	public boolean isRegistrationRevocationSupported() {
		return this.registrationRevocationSupported;
	}

	public boolean isProgressiveCallResultsSupported() {
		return this.progressiveCallResultsSupported;
	}

	public boolean isProgressiveCallInvocationsSupported() {
		return this.progressiveCallInvocationsSupported;
	}

	public boolean isCallRerouteSupported() {
		return this.callRerouteSupported;
	}

	public InvocationPolicy getInvocationPolicy() {
		return this.invocationPolicy;
	}

	public int getPrefixComponentCount() {
		return this.prefixComponentCount;
	}

	public List<Integer> getWildcardSpecificity() {
		return this.wildcardSpecificity;
	}

	@Override
	public String toString() {
		return "Procedure [procedure=" + this.procedure + ", webSocketSessionId=" + this.webSocketSessionId
				+ ", wampSessionId=" + this.wampSessionId + ", matchPolicy=" + this.matchPolicy + ", invocationPolicy="
				+ this.invocationPolicy + ", discloseCaller=" + this.discloseCaller + ", callCancelingSupported="
				+ this.callCancelingSupported + ", callTimeoutSupported=" + this.callTimeoutSupported
				+ ", progressiveCallResultsSupported=" + this.progressiveCallResultsSupported
				+ ", progressiveCallInvocationsSupported=" + this.progressiveCallInvocationsSupported
				+ ", registrationRevocationSupported=" + this.registrationRevocationSupported
				+ ", callRerouteSupported=" + this.callRerouteSupported + ", registrationId=" + this.registrationId
				+ ", pendingInvocations=" + this.pendingInvocations + "]";
	}

	private static int componentCount(String procedure) {
		return procedure.split("\\.", -1).length;
	}

	private static List<Integer> wildcardSpecificity(String procedure) {
		String[] components = procedure.split("\\.", -1);
		java.util.ArrayList<Integer> specificity = new java.util.ArrayList<>();
		int matchedComponents = 0;
		for (String component : components) {
			if (component.isEmpty()) {
				specificity.add(matchedComponents);
				matchedComponents = 0;
			}
			else {
				matchedComponents++;
			}
		}
		specificity.add(matchedComponents);
		return List.copyOf(specificity);
	}

}
