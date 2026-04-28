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
package ch.rasc.wamp2spring.message;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.rpc.Procedure;
import ch.rasc.wamp2spring.util.IdGenerator;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/**
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict]
 *
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list]
 *
 * [INVOCATION, Request|id, REGISTERED.Registration|id, Details|dict, CALL.Arguments|list,
 * CALL.ArgumentsKw|dict]
 */
public class InvocationMessage extends WampMessage {

	private static final AtomicLong lastRequest = new AtomicLong(1L);

	public static final int CODE = 68;

	private final long requestId;

	private final long registrationId;

	@Nullable private final List<Object> arguments;

	@Nullable private final Number caller;

	@Nullable private final String procedure;

	@Nullable private final String callerAuthId;

	@Nullable private final String callerAuthRole;

	@Nullable private final Number callerTrustLevel;

	@Nullable private final Long timeout;

	private final boolean progress;

	private final boolean receiveProgress;

	@Nullable private final Map<String, Object> argumentsKw;

	public InvocationMessage(long requestId, long registrationId, @Nullable Number caller, @Nullable Long timeout,
			@Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		this(requestId, registrationId, caller, null, null, null, null, false, false, timeout, arguments, argumentsKw);
	}

	public InvocationMessage(long requestId, long registrationId, @Nullable Number caller,
			@Nullable String callerAuthId, @Nullable String callerAuthRole, @Nullable Long timeout,
			@Nullable List<Object> arguments, @Nullable Map<String, Object> argumentsKw) {
		this(requestId, registrationId, caller, null, callerAuthId, callerAuthRole, null, false, false, timeout,
				arguments, argumentsKw);
	}

	public InvocationMessage(long requestId, long registrationId, @Nullable Number caller, @Nullable String procedure,
			@Nullable String callerAuthId, @Nullable String callerAuthRole, @Nullable Number callerTrustLevel,
			boolean receiveProgress, @Nullable Long timeout, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		this(requestId, registrationId, caller, procedure, callerAuthId, callerAuthRole, callerTrustLevel, false,
				receiveProgress, timeout, arguments, argumentsKw);
	}

	public InvocationMessage(long requestId, long registrationId, @Nullable Number caller, @Nullable String procedure,
			@Nullable String callerAuthId, @Nullable String callerAuthRole, @Nullable Number callerTrustLevel,
			boolean progress, boolean receiveProgress, @Nullable Long timeout, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) {
		super(CODE);
		this.requestId = requestId;
		this.registrationId = registrationId;
		this.caller = caller;
		this.procedure = procedure;
		this.callerAuthId = callerAuthId;
		this.callerAuthRole = callerAuthRole;
		this.callerTrustLevel = callerTrustLevel;
		this.progress = progress;
		this.receiveProgress = receiveProgress;
		this.timeout = timeout;
		this.arguments = arguments;
		this.argumentsKw = argumentsKw;
	}

	public InvocationMessage(Procedure procedure, CallMessage callMessage) {
		this(IdGenerator.newLinearId(lastRequest), procedure, callMessage, callMessage);
	}

	public InvocationMessage(long requestId, Procedure procedure, CallMessage callMessage, CallMessage optionsSource) {
		this(requestId, procedure.getRegistrationId(), getDisclosedCaller(procedure, optionsSource),
				procedure.getMatchPolicy() != MatchPolicy.EXACT ? callMessage.getProcedure() : null,
				getDisclosedCallerAuthId(procedure, optionsSource),
				getDisclosedCallerAuthRole(procedure, optionsSource),
				getDisclosedCallerTrustLevel(procedure, optionsSource),
				procedure.isProgressiveCallInvocationsSupported() && callMessage.isProgress(),
				procedure.isProgressiveCallResultsSupported() && optionsSource.isReceiveProgress(),
				procedure.isCallTimeoutSupported() ? optionsSource.getTimeout() : null, callMessage.getArguments(),
				callMessage.getArgumentsKw());
		setReceiverWebSocketSessionId(procedure.getWebSocketSessionId());
	}

	public static InvocationMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		jp.nextToken();
		long registration = jp.getLongValue();

		jp.nextToken();
		Number caller = null;
		String procedure = null;
		String callerAuthId = null;
		String callerAuthRole = null;
		Number callerTrustLevel = null;
		boolean progress = false;
		boolean receiveProgress = false;
		Long timeout = null;
		Map<String, Object> details = ParserUtil.readObject(jp);
		if (details != null) {
			caller = (Number) details.get("caller");
			procedure = (String) details.get("procedure");
			callerAuthId = (String) details.get("caller_authid");
			callerAuthRole = (String) details.get("caller_authrole");
			callerTrustLevel = (Number) details.get("caller_trustlevel");
			progress = (boolean) details.getOrDefault("progress", false);
			receiveProgress = (boolean) details.getOrDefault("receive_progress", false);
			Object timeoutValue = details.get("timeout");
			if (timeoutValue instanceof Number timeoutNumber) {
				timeout = timeoutNumber.longValue();
			}
		}

		List<Object> arguments = null;
		JsonToken token = jp.nextToken();
		if (token == JsonToken.START_ARRAY) {
			arguments = ParserUtil.readArray(jp);
		}

		Map<String, Object> argumentsKw = null;
		token = jp.nextToken();
		if (token == JsonToken.START_OBJECT) {
			argumentsKw = ParserUtil.readObject(jp);
		}

		return new InvocationMessage(request, registration, caller, procedure, callerAuthId, callerAuthRole,
				callerTrustLevel, progress, receiveProgress, timeout, arguments, argumentsKw);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);
		generator.writeNumber(this.registrationId);
		generator.writeStartObject();
		Number callerValue = this.caller;
		if (callerValue != null) {
			generator.writeName("caller");
			generator.writeNumber(callerValue.longValue());
		}
		if (this.procedure != null) {
			generator.writeStringProperty("procedure", this.procedure);
		}
		if (this.callerAuthId != null) {
			generator.writeStringProperty("caller_authid", this.callerAuthId);
		}
		if (this.callerAuthRole != null) {
			generator.writeStringProperty("caller_authrole", this.callerAuthRole);
		}
		if (this.callerTrustLevel != null) {
			generator.writeNumberProperty("caller_trustlevel", this.callerTrustLevel.longValue());
		}
		if (this.progress) {
			generator.writeBooleanProperty("progress", true);
		}
		if (this.receiveProgress) {
			generator.writeBooleanProperty("receive_progress", true);
		}
		if (this.timeout != null) {
			generator.writeNumberProperty("timeout", this.timeout);
		}
		generator.writeEndObject();

		if (this.argumentsKw != null) {
			if (this.arguments == null) {
				generator.writeStartArray();
				generator.writeEndArray();
			}
			else {
				generator.writePOJO(this.arguments);
			}
			generator.writePOJO(this.argumentsKw);
		}
		else if (this.arguments != null) {
			generator.writePOJO(this.arguments);
		}
	}

	public long getRequestId() {
		return this.requestId;
	}

	public long getRegistrationId() {
		return this.registrationId;
	}

	@Nullable public List<Object> getArguments() {
		return this.arguments;
	}

	@Nullable public Map<String, Object> getArgumentsKw() {
		return this.argumentsKw;
	}

	@Nullable public Number getCaller() {
		return this.caller;
	}

	@Nullable public String getProcedure() {
		return this.procedure;
	}

	@Nullable public String getCallerAuthId() {
		return this.callerAuthId;
	}

	@Nullable public String getCallerAuthRole() {
		return this.callerAuthRole;
	}

	@Nullable public Number getCallerTrustLevel() {
		return this.callerTrustLevel;
	}

	@Nullable public Long getTimeout() {
		return this.timeout;
	}

	public boolean isProgress() {
		return this.progress;
	}

	public boolean isReceiveProgress() {
		return this.receiveProgress;
	}

	@Override
	public String toString() {
		return "InvocationMessage [requestId=" + this.requestId + ", registrationId=" + this.registrationId
				+ ", arguments=" + this.arguments + ", caller=" + this.caller + ", procedure=" + this.procedure
				+ ", callerAuthId=" + this.callerAuthId + ", callerAuthRole=" + this.callerAuthRole
				+ ", callerTrustLevel=" + this.callerTrustLevel + ", progress=" + this.progress + ", receiveProgress="
				+ this.receiveProgress + ", timeout=" + this.timeout + ", argumentsKw=" + this.argumentsKw + "]";
	}

	@Nullable private static Number getDisclosedCaller(Procedure procedure, CallMessage callMessage) {
		return shouldDiscloseCaller(procedure, callMessage) ? callMessage.getWampSessionId() : null;
	}

	@Nullable private static String getDisclosedCallerAuthId(Procedure procedure, CallMessage callMessage) {
		return shouldDiscloseCaller(procedure, callMessage) ? callMessage.getAuthId() : null;
	}

	@Nullable private static String getDisclosedCallerAuthRole(Procedure procedure, CallMessage callMessage) {
		return shouldDiscloseCaller(procedure, callMessage) ? callMessage.getAuthRole() : null;
	}

	@Nullable private static Number getDisclosedCallerTrustLevel(Procedure procedure, CallMessage callMessage) {
		return shouldDiscloseCaller(procedure, callMessage) ? callMessage.getTrustLevel() : null;
	}

	private static boolean shouldDiscloseCaller(Procedure procedure, CallMessage callMessage) {
		return procedure.isDiscloseCaller() || callMessage.isDiscloseMe();
	}

}
