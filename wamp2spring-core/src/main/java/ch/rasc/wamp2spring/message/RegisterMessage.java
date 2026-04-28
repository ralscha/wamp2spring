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
import java.util.Map;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.rpc.InvocationPolicy;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * [REGISTER, Request|id, Options|dict, Procedure|uri]
 */
public class RegisterMessage extends WampMessage {

	public static final int CODE = 64;

	private final long requestId;

	private final String procedure;

	private final boolean discloseCaller;

	private final MatchPolicy matchPolicy;

	private final InvocationPolicy invokePolicy;

	public RegisterMessage(long requestId, String procedure) {
		this(requestId, procedure, false, MatchPolicy.EXACT, InvocationPolicy.SINGLE);
	}

	public RegisterMessage(long requestId, String procedure, boolean discloseCaller) {
		this(requestId, procedure, discloseCaller, MatchPolicy.EXACT, InvocationPolicy.SINGLE);
	}

	public RegisterMessage(long requestId, String procedure, MatchPolicy matchPolicy) {
		this(requestId, procedure, false, matchPolicy, InvocationPolicy.SINGLE);
	}

	public RegisterMessage(long requestId, String procedure, boolean discloseCaller, MatchPolicy matchPolicy) {
		this(requestId, procedure, discloseCaller, matchPolicy, InvocationPolicy.SINGLE);
	}

	public RegisterMessage(long requestId, String procedure, boolean discloseCaller, MatchPolicy matchPolicy,
			InvocationPolicy invokePolicy) {
		super(CODE);
		this.requestId = requestId;
		this.procedure = procedure;
		this.discloseCaller = discloseCaller;
		this.matchPolicy = matchPolicy;
		this.invokePolicy = invokePolicy;
	}

	public static RegisterMessage deserialize(JsonParser jp) throws IOException {
		jp.nextToken();
		long request = jp.getLongValue();

		boolean discloseCaller = false;
		MatchPolicy matchPolicy = MatchPolicy.EXACT;
		InvocationPolicy invokePolicy = InvocationPolicy.SINGLE;
		jp.nextToken();
		Map<String, Object> options = ParserUtil.readObject(jp);
		if (options != null) {
			discloseCaller = (boolean) options.getOrDefault("disclose_caller", false);
			String extValue = (String) options.get("match");
			if (extValue != null) {
				matchPolicy = MatchPolicy.fromExtValue(extValue);
				if (matchPolicy == null) {
					matchPolicy = MatchPolicy.EXACT;
				}
			}
			extValue = (String) options.get("invoke");
			if (extValue != null) {
				invokePolicy = InvocationPolicy.fromExternalValue(extValue);
				if (invokePolicy == null) {
					invokePolicy = InvocationPolicy.SINGLE;
				}
			}
		}

		jp.nextToken();
		String procedure = jp.getValueAsString();

		return new RegisterMessage(request, procedure, discloseCaller, matchPolicy, invokePolicy);
	}

	@Override
	public void serialize(JsonGenerator generator) throws IOException {
		generator.writeNumber(getCode());
		generator.writeNumber(this.requestId);

		generator.writeStartObject();
		if (this.discloseCaller) {
			generator.writeBooleanProperty("disclose_caller", this.discloseCaller);
		}
		if (this.matchPolicy != MatchPolicy.EXACT) {
			generator.writeStringProperty("match", this.matchPolicy.getExternalValue());
		}
		if (this.invokePolicy != InvocationPolicy.SINGLE) {
			generator.writeStringProperty("invoke", this.invokePolicy.getExternalValue());
		}
		generator.writeEndObject();

		generator.writeString(this.procedure);

	}

	public long getRequestId() {
		return this.requestId;
	}

	public String getProcedure() {
		return this.procedure;
	}

	public boolean isDiscloseCaller() {
		return this.discloseCaller;
	}

	public MatchPolicy getMatchPolicy() {
		return this.matchPolicy;
	}

	public InvocationPolicy getInvokePolicy() {
		return this.invokePolicy;
	}

	@Override
	public String toString() {
		return "RegisterMessage [requestId=" + this.requestId + ", procedure=" + this.procedure + ", discloseCaller="
				+ this.discloseCaller + ", matchPolicy=" + this.matchPolicy + ", invokePolicy=" + this.invokePolicy
				+ "]";
	}

}
