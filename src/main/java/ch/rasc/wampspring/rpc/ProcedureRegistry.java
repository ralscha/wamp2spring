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
package ch.rasc.wampspring.rpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import ch.rasc.wampspring.WampError;
import ch.rasc.wampspring.message.CallMessage;
import ch.rasc.wampspring.message.ErrorMessage;
import ch.rasc.wampspring.message.InvocationMessage;
import ch.rasc.wampspring.message.RegisterMessage;
import ch.rasc.wampspring.message.UnregisterMessage;
import ch.rasc.wampspring.message.WampMessage;
import ch.rasc.wampspring.message.YieldMessage;
import ch.rasc.wampspring.util.IdGenerator;

public class ProcedureRegistry {
	private final static AtomicLong lastRegistration = new AtomicLong(1L);

	private final Map<String, Procedure> procedures = new ConcurrentHashMap<>();
	private final Map<Long, String> registrations = new ConcurrentHashMap<>();

	private final Map<Long, CallProc> pendingInvocations = new ConcurrentHashMap<>();

	synchronized long register(RegisterMessage registerMessage) {
		if (!this.procedures.containsKey(registerMessage.getProcedure())) {
			long registrationId = IdGenerator.newLinearId(lastRegistration);
			this.registrations.put(registrationId, registerMessage.getProcedure());

			Procedure procedure = new Procedure(registerMessage.getProcedure(),
					registerMessage.getWebSocketSessionId(), registrationId);
			this.procedures.put(registerMessage.getProcedure(), procedure);
			return registrationId;
		}
		return -1;
	}

	synchronized UnregisterResult unregister(UnregisterMessage unregisterMessage) {
		String procedure = this.registrations
				.remove(unregisterMessage.getRegistrationId());

		if (procedure != null) {
			Procedure proc = this.procedures.remove(procedure);
			return new UnregisterResult(true, procedure,
					createErrorsForPendingInvocations(proc));
		}

		return new UnregisterResult(false, null);
	}

	synchronized List<UnregisterResult> unregisterWebSocketSession(
			String webSocketSessionId) {

		List<UnregisterResult> unregisterResults = new ArrayList<>();

		List<Procedure> toRemoveProcedures = this.procedures.values().stream()
				.filter(proc -> proc.getWebSocketSessionId().equals(webSocketSessionId))
				.collect(Collectors.toList());

		for (Procedure proc : toRemoveProcedures) {
			this.procedures.remove(proc.getProcedure());
			this.registrations.remove(proc.getRegistrationId());

			List<ErrorMessage> errorsForPendingInvocations = createErrorsForPendingInvocations(
					proc);

			UnregisterResult result = new UnregisterResult(true, proc.getProcedure(),
					errorsForPendingInvocations);
			unregisterResults.add(result);
		}

		return unregisterResults;
	}

	private static List<ErrorMessage> createErrorsForPendingInvocations(Procedure proc) {
		List<ErrorMessage> errorMessages = new ArrayList<>();
		for (Long invocationRequestId : proc.getPendingInvocations()) {
			errorMessages.add(new ErrorMessage(InvocationMessage.CODE,
					invocationRequestId,
					WampError.NO_SUCH_REGISTRATION.getExternalValue(), null, null));
		}
		return errorMessages;
	}

	WampMessage createInvocationMessage(CallMessage callMessage) {
		Procedure procedure = this.procedures.get(callMessage.getProcedure());
		if (procedure != null) {
			InvocationMessage invocationMessage = new InvocationMessage(procedure,
					callMessage);
			this.pendingInvocations.put(invocationMessage.getRequestId(),
					new CallProc(callMessage, procedure));
			procedure.addPendingInvocation(invocationMessage.getRequestId());
			return invocationMessage;
		}

		return new ErrorMessage(callMessage, WampError.NO_SUCH_PROCEDURE);
	}

	@Nullable
	CallMessage removeInvocationCall(WampMessage yieldOrErrorMessage) {
		long requestId;
		if (yieldOrErrorMessage instanceof YieldMessage) {
			requestId = ((YieldMessage) yieldOrErrorMessage).getRequestId();
		}
		else if (yieldOrErrorMessage instanceof ErrorMessage) {
			requestId = ((ErrorMessage) yieldOrErrorMessage).getRequestId();
		}
		else {
			return null;
		}

		CallProc callProc = this.pendingInvocations.remove(requestId);
		if (callProc != null) {
			callProc.procedure.removePendingInvocation(requestId);
			return callProc.callMessage;
		}

		return null;
	}

	static class CallProc {
		CallMessage callMessage;
		Procedure procedure;

		public CallProc(CallMessage callMessage, Procedure procedure) {
			this.callMessage = callMessage;
			this.procedure = procedure;
		}
	}
}
