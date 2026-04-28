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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.DestinationMatch;
import ch.rasc.wamp2spring.config.Feature;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.CancelMessage;
import ch.rasc.wamp2spring.message.ErrorMessage;
import ch.rasc.wamp2spring.message.InvocationMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.UnregisterMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.YieldMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.util.IdGenerator;

public class ProcedureRegistry {

	private final AtomicLong lastRegistration = new AtomicLong(1L);

	private final EnumMap<MatchPolicy, Map<ProcedureKey, ProcedureSlot>> proceduresByMatch = new EnumMap<>(
			MatchPolicy.class);

	private final Map<Long, ProcedureSlot> registrations = new ConcurrentHashMap<>();

	private final Map<Long, CallProc> pendingInvocations = new ConcurrentHashMap<>();

	private final Map<CallKey, Long> pendingCalls = new ConcurrentHashMap<>();

	private final Features features;

	public ProcedureRegistry(Features features) {
		this.features = features;
		this.proceduresByMatch.put(MatchPolicy.EXACT, new ConcurrentHashMap<>());
		this.proceduresByMatch.put(MatchPolicy.PREFIX, new ConcurrentHashMap<>());
		this.proceduresByMatch.put(MatchPolicy.WILDCARD, new ConcurrentHashMap<>());
	}

	synchronized RegisterResult register(RegisterMessage registerMessage) {
		Map<ProcedureKey, ProcedureSlot> procedures = proceduresFor(registerMessage.getMatchPolicy());
		ProcedureKey procedureKey = new ProcedureKey(registerMessage.getProcedure());
		ProcedureSlot procedureSlot = procedures.get(procedureKey);
		if (procedureSlot != null && !procedureSlot.canRegister(registerMessage)) {
			return RegisterResult.failed();
		}

		if (procedureSlot == null) {
			long registrationId = IdGenerator.newLinearId(this.lastRegistration);
			Procedure procedure = new Procedure(registerMessage, registrationId,
					this.features.isEnabled(Feature.DEALER_CALLER_IDENTIFICATION));
			procedureSlot = new ProcedureSlot(procedure);
			procedures.put(procedureKey, procedureSlot);
			this.registrations.put(registrationId, procedureSlot);
			return RegisterResult.success(registrationId, true);
		}

		Procedure procedure = new Procedure(registerMessage, procedureSlot.getRegistrationId(),
				this.features.isEnabled(Feature.DEALER_CALLER_IDENTIFICATION));
		if (!procedureSlot.addProcedure(procedure)) {
			return RegisterResult.failed();
		}
		return RegisterResult.success(procedureSlot.getRegistrationId(), false);
	}

	synchronized UnregisterResult unregister(UnregisterMessage unregisterMessage) {
		ProcedureSlot procedureSlot = this.registrations.get(unregisterMessage.getRegistrationId());
		String webSocketSessionId = unregisterMessage.getWebSocketSessionId();
		if (procedureSlot == null || webSocketSessionId == null) {
			return new UnregisterResult(false, null, false);
		}

		Procedure proc = procedureSlot.removeProcedure(webSocketSessionId);
		if (proc != null) {
			boolean deleted = removeProcedureSlotIfEmpty(procedureSlot);
			return new UnregisterResult(true, proc, deleted, createErrorsForPendingInvocations(proc));
		}

		return new UnregisterResult(false, null, false);
	}

	synchronized List<UnregisterResult> unregisterWebSocketSession(String webSocketSessionId) {

		List<UnregisterResult> unregisterResults = new ArrayList<>();

		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			Map<ProcedureKey, ProcedureSlot> procedures = proceduresFor(matchPolicy);
			for (ProcedureSlot procedureSlot : procedures.values()) {
				Procedure proc = procedureSlot.removeProcedure(webSocketSessionId);
				if (proc == null) {
					continue;
				}

				boolean deleted = removeProcedureSlotIfEmpty(procedureSlot);
				List<ErrorMessage> errorsForPendingInvocations = createErrorsForPendingInvocations(proc);
				unregisterResults.add(new UnregisterResult(true, proc, deleted, errorsForPendingInvocations));
			}
		}

		return unregisterResults;
	}

	private static List<ErrorMessage> createErrorsForPendingInvocations(Procedure proc) {
		List<ErrorMessage> errorMessages = new ArrayList<>();
		for (Long invocationRequestId : proc.getPendingInvocations()) {
			errorMessages.add(new ErrorMessage(InvocationMessage.CODE, invocationRequestId,
					WampError.NO_SUCH_REGISTRATION.getExternalValue(), null, null));
		}
		return errorMessages;
	}

	private boolean removeProcedureSlotIfEmpty(ProcedureSlot procedureSlot) {
		if (!procedureSlot.isEmpty()) {
			return false;
		}

		proceduresFor(procedureSlot.getMatchPolicy()).remove(new ProcedureKey(procedureSlot.getProcedure()));
		this.registrations.remove(procedureSlot.getRegistrationId());
		return true;
	}

	synchronized List<UnregisterResult> revokeRegistration(long registrationId) {
		ProcedureSlot procedureSlot = this.registrations.get(registrationId);
		if (procedureSlot == null) {
			return List.of();
		}

		List<Procedure> procedures = procedureSlot.listProcedures();
		if (procedures.isEmpty()) {
			return List.of();
		}

		for (Procedure procedure : procedures) {
			procedureSlot.removeProcedure(procedure.getWebSocketSessionId());
		}

		boolean deleted = removeProcedureSlotIfEmpty(procedureSlot);
		List<UnregisterResult> unregisterResults = new ArrayList<>(procedures.size());
		for (int i = 0; i < procedures.size(); i++) {
			Procedure procedure = procedures.get(i);
			unregisterResults.add(new UnregisterResult(true, procedure, deleted && i == procedures.size() - 1,
					createErrorsForPendingInvocations(procedure)));
		}
		return unregisterResults;
	}

	synchronized WampMessage createInvocationMessage(CallMessage callMessage) {
		ProcedureSlot procedureSlot = findProcedureSlot(callMessage.getProcedure());
		if (procedureSlot == null) {
			return new ErrorMessage(callMessage, WampError.NO_SUCH_PROCEDURE);
		}
		if (procedureSlot.getInvocationPolicy() == InvocationPolicy.SHARDED && callMessage.getRkey() == null) {
			return new ErrorMessage(callMessage, WampError.INVALID_ARGUMENT);
		}

		Procedure procedure = procedureSlot.selectProcedure(callMessage.getRkey());
		if (procedure != null) {
			String webSocketSessionId = callMessage.getWebSocketSessionId();
			if (webSocketSessionId != null) {
				CallKey callKey = new CallKey(webSocketSessionId, callMessage.getRequestId());
				Long continuationInvocationRequestId = this.pendingCalls.get(callKey);
				if (continuationInvocationRequestId != null) {
					CallProc existingCallProc = this.pendingInvocations.get(continuationInvocationRequestId);
					if (existingCallProc == null) {
						this.pendingCalls.remove(callKey);
					}
					else if (!existingCallProc.progressiveCallInvocation || !Objects
						.equals(existingCallProc.callMessage.getProcedure(), callMessage.getProcedure())) {
						return new ErrorMessage(callMessage, WampError.PROTOCOL_VIOLATION);
					}
					else {
						return new InvocationMessage(continuationInvocationRequestId, existingCallProc.procedure,
								callMessage, existingCallProc.callMessage);
					}
				}
			}

			if (callMessage.isProgress() && !procedure.isProgressiveCallInvocationsSupported()) {
				return new ErrorMessage(callMessage, WampError.FEATURE_NOT_SUPPORTED);
			}

			if (callMessage.isReceiveProgress() && !procedure.isProgressiveCallResultsSupported()) {
				return new ErrorMessage(callMessage, WampError.FEATURE_NOT_SUPPORTED);
			}
			InvocationMessage invocationMessage = new InvocationMessage(procedure, callMessage);
			CallProc callProc = new CallProc(callMessage, procedure);
			callProc.markAttempted(procedure);
			this.pendingInvocations.put(invocationMessage.getRequestId(), callProc);
			if (callProc.callKey != null) {
				this.pendingCalls.put(callProc.callKey, invocationMessage.getRequestId());
			}
			procedure.addPendingInvocation(invocationMessage.getRequestId());
			return invocationMessage;
		}

		return new ErrorMessage(callMessage, WampError.NO_SUCH_PROCEDURE);
	}

	@Nullable private ProcedureSlot findProcedureSlot(String procedureUri) {
		ProcedureSlot exactProcedure = proceduresFor(MatchPolicy.EXACT).get(new ProcedureKey(procedureUri));
		if (exactProcedure != null) {
			return exactProcedure;
		}

		ProcedureSlot prefixProcedure = null;
		for (ProcedureSlot procedure : proceduresFor(MatchPolicy.PREFIX).values()) {
			if (procedure.getProcedureMatch().matches(procedureUri) && (prefixProcedure == null
					|| procedure.getPrefixComponentCount() > prefixProcedure.getPrefixComponentCount())) {
				prefixProcedure = procedure;
			}
		}
		if (prefixProcedure != null) {
			return prefixProcedure;
		}

		ProcedureSlot wildcardProcedure = null;
		for (ProcedureSlot procedure : proceduresFor(MatchPolicy.WILDCARD).values()) {
			if (!procedure.getProcedureMatch().matches(procedureUri)) {
				continue;
			}
			if (wildcardProcedure == null || compareWildcardSpecificity(procedure, wildcardProcedure) > 0) {
				wildcardProcedure = procedure;
			}
		}

		return wildcardProcedure;
	}

	public EnumMap<MatchPolicy, List<Long>> listRegistrations() {
		EnumMap<MatchPolicy, List<Long>> result = new EnumMap<>(MatchPolicy.class);

		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			List<Long> registrationIds = proceduresFor(matchPolicy).values()
				.stream()
				.map(ProcedureSlot::getRegistrationId)
				.toList();
			result.put(matchPolicy, registrationIds);
		}

		return result;
	}

	@Nullable public Long lookupRegistration(String procedure, @Nullable MatchPolicy matchPolicy) {
		MatchPolicy effectiveMatchPolicy = matchPolicy != null ? matchPolicy : MatchPolicy.EXACT;
		ProcedureSlot procedureSlot = proceduresFor(effectiveMatchPolicy).get(new ProcedureKey(procedure));
		return procedureSlot != null ? procedureSlot.getRegistrationId() : null;
	}

	private Map<ProcedureKey, ProcedureSlot> proceduresFor(MatchPolicy matchPolicy) {
		return Objects.requireNonNull(this.proceduresByMatch.get(matchPolicy));
	}

	@Nullable public Long matchRegistration(String procedureUri) {
		ProcedureSlot procedureSlot = findProcedureSlot(procedureUri);
		return procedureSlot != null ? procedureSlot.getRegistrationId() : null;
	}

	@Nullable public ProcedureDetail getRegistration(long registrationId) {
		ProcedureSlot procedureSlot = this.registrations.get(registrationId);
		return procedureSlot != null ? procedureSlot.toDetail() : null;
	}

	public List<Long> listCallees(long registrationId) {
		ProcedureSlot procedureSlot = this.registrations.get(registrationId);
		if (procedureSlot == null) {
			return List.of();
		}

		return procedureSlot.listCallees();
	}

	@Nullable public Integer countCallees(long registrationId) {
		ProcedureSlot procedureSlot = this.registrations.get(registrationId);
		return procedureSlot != null ? procedureSlot.countCallees() : null;
	}

	private static int compareWildcardSpecificity(ProcedureSlot candidate, ProcedureSlot current) {
		List<Integer> candidateSpecificity = candidate.getWildcardSpecificity();
		List<Integer> currentSpecificity = current.getWildcardSpecificity();
		int limit = Math.min(candidateSpecificity.size(), currentSpecificity.size());
		for (int i = 0; i < limit; i++) {
			int comparison = Integer.compare(candidateSpecificity.get(i), currentSpecificity.get(i));
			if (comparison != 0) {
				return comparison;
			}
		}
		return Integer.compare(candidateSpecificity.size(), currentSpecificity.size());
	}

	@Nullable synchronized PendingCall getPendingCall(CancelMessage cancelMessage) {
		String webSocketSessionId = cancelMessage.getWebSocketSessionId();
		if (webSocketSessionId == null) {
			return null;
		}

		Long invocationRequestId = this.pendingCalls.get(new CallKey(webSocketSessionId, cancelMessage.getRequestId()));
		if (invocationRequestId == null) {
			return null;
		}

		CallProc callProc = this.pendingInvocations.get(invocationRequestId);
		if (callProc == null) {
			this.pendingCalls.remove(new CallKey(webSocketSessionId, cancelMessage.getRequestId()));
			return null;
		}

		return new PendingCall(invocationRequestId, callProc.callMessage, callProc.procedure);
	}

	synchronized void removePendingCall(PendingCall pendingCall) {
		removePendingInvocation(pendingCall.getInvocationRequestId());
	}

	@Nullable synchronized PendingCall removePendingInvocation(long invocationRequestId) {
		CallProc callProc = this.pendingInvocations.remove(invocationRequestId);
		if (callProc == null) {
			return null;
		}

		callProc.procedure.removePendingInvocation(invocationRequestId);
		if (callProc.callKey != null) {
			this.pendingCalls.remove(callProc.callKey);
		}

		return new PendingCall(invocationRequestId, callProc.callMessage, callProc.procedure);
	}

	synchronized List<PendingCall> removePendingCalls(String webSocketSessionId) {
		List<PendingCall> removedPendingCalls = new ArrayList<>();

		List<Map.Entry<CallKey, Long>> callsToRemove = this.pendingCalls.entrySet()
			.stream()
			.filter(entry -> entry.getKey().webSocketSessionId.equals(webSocketSessionId))
			.toList();

		for (Map.Entry<CallKey, Long> entry : callsToRemove) {
			Long invocationRequestId = this.pendingCalls.remove(entry.getKey());
			if (invocationRequestId == null) {
				continue;
			}

			CallProc callProc = this.pendingInvocations.remove(invocationRequestId);
			if (callProc == null) {
				continue;
			}

			callProc.procedure.removePendingInvocation(invocationRequestId);
			removedPendingCalls.add(new PendingCall(invocationRequestId, callProc.callMessage, callProc.procedure));
		}

		return removedPendingCalls;
	}

	@Nullable synchronized PendingCall getPendingInvocation(long invocationRequestId) {
		CallProc callProc = this.pendingInvocations.get(invocationRequestId);
		if (callProc == null) {
			return null;
		}

		return new PendingCall(invocationRequestId, callProc.callMessage, callProc.procedure);
	}

	@Nullable synchronized InvocationMessage rerouteInvocation(ErrorMessage errorMessage) {
		CallProc callProc = this.pendingInvocations.remove(errorMessage.getRequestId());
		if (callProc == null) {
			return null;
		}

		callProc.procedure.removePendingInvocation(errorMessage.getRequestId());
		ProcedureSlot procedureSlot = findProcedureSlot(callProc.callMessage.getProcedure());
		if (procedureSlot == null) {
			if (callProc.callKey != null) {
				this.pendingCalls.remove(callProc.callKey);
			}
			return null;
		}

		Procedure reroutedProcedure = procedureSlot.selectAlternativeProcedure(callProc.procedure,
				callProc.attemptedCalleeSessionIds);
		if (reroutedProcedure == null) {
			if (callProc.callKey != null) {
				this.pendingCalls.remove(callProc.callKey);
			}
			return null;
		}

		InvocationMessage invocationMessage = new InvocationMessage(reroutedProcedure, callProc.callMessage);
		callProc.markAttempted(reroutedProcedure);
		this.pendingInvocations.put(invocationMessage.getRequestId(), callProc);
		if (callProc.callKey != null) {
			this.pendingCalls.put(callProc.callKey, invocationMessage.getRequestId());
		}
		reroutedProcedure.addPendingInvocation(invocationMessage.getRequestId());
		return invocationMessage;
	}

	@Nullable synchronized CallMessage removeInvocationCall(WampMessage yieldOrErrorMessage) {
		long requestId;
		if (yieldOrErrorMessage instanceof YieldMessage yieldMessage) {
			requestId = yieldMessage.getRequestId();
		}
		else if (yieldOrErrorMessage instanceof ErrorMessage errorMessage) {
			requestId = errorMessage.getRequestId();
		}
		else {
			return null;
		}

		CallProc callProc = this.pendingInvocations.remove(requestId);
		if (callProc != null) {
			callProc.procedure.removePendingInvocation(requestId);
			if (callProc.callKey != null) {
				this.pendingCalls.remove(callProc.callKey);
			}
			return callProc.callMessage;
		}

		return null;
	}

	static class CallProc {

		final CallMessage callMessage;

		Procedure procedure;

		final boolean progressiveCallInvocation;

		@Nullable final CallKey callKey;

		final java.util.Set<String> attemptedCalleeSessionIds = new java.util.HashSet<>();

		public CallProc(CallMessage callMessage, Procedure procedure) {
			this.callMessage = callMessage;
			this.procedure = procedure;
			this.progressiveCallInvocation = callMessage.isProgress();
			String webSocketSessionId = callMessage.getWebSocketSessionId();
			this.callKey = webSocketSessionId != null ? new CallKey(webSocketSessionId, callMessage.getRequestId())
					: null;
		}

		void markAttempted(Procedure procedure) {
			this.procedure = procedure;
			this.attemptedCalleeSessionIds.add(procedure.getWebSocketSessionId());
		}

	}

	static class PendingCall {

		private final long invocationRequestId;

		private final CallMessage callMessage;

		private final Procedure procedure;

		PendingCall(long invocationRequestId, CallMessage callMessage, Procedure procedure) {
			this.invocationRequestId = invocationRequestId;
			this.callMessage = callMessage;
			this.procedure = procedure;
		}

		long getInvocationRequestId() {
			return this.invocationRequestId;
		}

		CallMessage getCallMessage() {
			return this.callMessage;
		}

		Procedure getProcedure() {
			return this.procedure;
		}

	}

	private static final class CallKey {

		private final String webSocketSessionId;

		private final long requestId;

		private CallKey(String webSocketSessionId, long requestId) {
			this.webSocketSessionId = webSocketSessionId;
			this.requestId = requestId;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof CallKey other)) {
				return false;
			}
			return this.requestId == other.requestId
					&& Objects.equals(this.webSocketSessionId, other.webSocketSessionId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.webSocketSessionId, this.requestId);
		}

	}

	private static final class ProcedureSlot {

		private final String procedure;

		private final long registrationId;

		private final long created;

		private final DestinationMatch procedureMatch;

		private final MatchPolicy matchPolicy;

		private final int prefixComponentCount;

		private final List<Integer> wildcardSpecificity;

		private final InvocationPolicy invocationPolicy;

		private final CopyOnWriteArrayList<Procedure> procedures = new CopyOnWriteArrayList<>();

		private int roundRobinIndex;

		private ProcedureSlot(Procedure procedure) {
			this.procedure = procedure.getProcedure();
			this.registrationId = procedure.getRegistrationId();
			this.created = System.currentTimeMillis();
			this.procedureMatch = procedure.getProcedureMatch();
			this.matchPolicy = procedure.getMatchPolicy();
			this.prefixComponentCount = procedure.getPrefixComponentCount();
			this.wildcardSpecificity = procedure.getWildcardSpecificity();
			this.invocationPolicy = procedure.getInvocationPolicy();
			this.procedures.add(procedure);
		}

		boolean canRegister(RegisterMessage registerMessage) {
			return this.invocationPolicy == registerMessage.getInvokePolicy()
					&& this.invocationPolicy != InvocationPolicy.SINGLE
					&& this.procedures.stream()
						.noneMatch(existingProcedure -> Objects.equals(existingProcedure.getWebSocketSessionId(),
								registerMessage.getWebSocketSessionId()));
		}

		boolean addProcedure(Procedure procedure) {
			if (this.procedures.stream()
				.anyMatch(existingProcedure -> Objects.equals(existingProcedure.getWebSocketSessionId(),
						procedure.getWebSocketSessionId()))) {
				return false;
			}
			this.procedures.add(procedure);
			return true;
		}

		@Nullable Procedure removeProcedure(String webSocketSessionId) {
			for (Procedure registeredProcedure : this.procedures) {
				if (Objects.equals(registeredProcedure.getWebSocketSessionId(), webSocketSessionId)) {
					if (this.procedures.remove(registeredProcedure) && this.roundRobinIndex >= this.procedures.size()) {
						this.roundRobinIndex = 0;
					}
					return registeredProcedure;
				}
			}

			return null;
		}

		long getRegistrationId() {
			return this.registrationId;
		}

		String getProcedure() {
			return this.procedure;
		}

		MatchPolicy getMatchPolicy() {
			return this.matchPolicy;
		}

		InvocationPolicy getInvocationPolicy() {
			return this.invocationPolicy;
		}

		boolean isEmpty() {
			return this.procedures.isEmpty();
		}

		Procedure selectProcedure(@Nullable String rkey) {
			int size = this.procedures.size();
			if (size == 1) {
				return this.procedures.get(0);
			}

			switch (this.invocationPolicy) {
				case SHARDED:
					return this.procedures.get(Math.floorMod(Objects.requireNonNull(rkey).hashCode(), size));
				case ROUNDROBIN:
					Procedure roundRobinProcedure = this.procedures.get(this.roundRobinIndex % size);
					this.roundRobinIndex = (this.roundRobinIndex + 1) % size;
					return roundRobinProcedure;
				case RANDOM:
					return this.procedures.get(ThreadLocalRandom.current().nextInt(size));
				case LAST:
					return this.procedures.get(size - 1);
				case FIRST:
				case SINGLE:
				default:
					return this.procedures.get(0);
			}
		}

		@Nullable Procedure selectAlternativeProcedure(Procedure currentProcedure, java.util.Set<String> excludedCallees) {
			if (this.procedures.size() <= 1) {
				return null;
			}

			int currentIndex = this.procedures.indexOf(currentProcedure);
			if (currentIndex < 0) {
				return null;
			}

			switch (this.invocationPolicy) {
				case ROUNDROBIN:
					for (int offset = 1; offset < this.procedures.size(); offset++) {
						Procedure candidate = this.procedures.get((currentIndex + offset) % this.procedures.size());
						if (!excludedCallees.contains(candidate.getWebSocketSessionId())) {
							return candidate;
						}
					}
					return null;
				case RANDOM:
					List<Procedure> candidates = this.procedures.stream()
						.filter(candidate -> !excludedCallees.contains(candidate.getWebSocketSessionId()))
						.toList();
					if (candidates.isEmpty()) {
						return null;
					}
					return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
				case FIRST:
					for (int i = currentIndex + 1; i < this.procedures.size(); i++) {
						Procedure candidate = this.procedures.get(i);
						if (!excludedCallees.contains(candidate.getWebSocketSessionId())) {
							return candidate;
						}
					}
					return null;
				case LAST:
					for (int i = currentIndex - 1; i >= 0; i--) {
						Procedure candidate = this.procedures.get(i);
						if (!excludedCallees.contains(candidate.getWebSocketSessionId())) {
							return candidate;
						}
					}
					return null;
				case SHARDED:
				case SINGLE:
				default:
					return null;
			}
		}

		DestinationMatch getProcedureMatch() {
			return this.procedureMatch;
		}

		int getPrefixComponentCount() {
			return this.prefixComponentCount;
		}

		List<Integer> getWildcardSpecificity() {
			return this.wildcardSpecificity;
		}

		int countCallees() {
			return this.procedures.size();
		}

		List<Long> listCallees() {
			return this.procedures.stream().map(Procedure::getWampSessionId).filter(Objects::nonNull).toList();
		}

		List<Procedure> listProcedures() {
			return new ArrayList<>(this.procedures);
		}

		ProcedureDetail toDetail() {
			return new ProcedureDetail(this.registrationId, this.created, this.procedure, this.matchPolicy,
					this.invocationPolicy);
		}

		@Override
		public String toString() {
			return "ProcedureSlot [procedure=" + this.procedure + ", registrationId=" + this.registrationId
					+ ", invocationPolicy=" + this.invocationPolicy + ", procedures=" + this.procedures + "]";
		}

	}

	private record ProcedureKey(String procedure) {
		// map key
	}

}
