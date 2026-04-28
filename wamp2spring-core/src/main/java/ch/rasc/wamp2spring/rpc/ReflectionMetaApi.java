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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.pubsub.SubscriptionDetail;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;

public class ReflectionMetaApi {

	static final String PROCEDURE_LIST = "wamp.reflection.procedure.list";

	static final String PROCEDURE_DESCRIBE = "wamp.reflection.procedure.describe";

	static final String TOPIC_LIST = "wamp.reflection.topic.list";

	static final String TOPIC_DESCRIBE = "wamp.reflection.topic.describe";

	static final String ERROR_LIST = "wamp.reflection.error.list";

	static final String ERROR_DESCRIBE = "wamp.reflection.error.describe";

	private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
		.withZone(ZoneOffset.UTC);

	private final ProcedureRegistry procedureRegistry;

	private final SubscriptionRegistry subscriptionRegistry;

	public ReflectionMetaApi(ProcedureRegistry procedureRegistry, SubscriptionRegistry subscriptionRegistry) {
		this.procedureRegistry = procedureRegistry;
		this.subscriptionRegistry = subscriptionRegistry;
	}

	public WampResult listProcedures() {
		return listProcedures((String) null);
	}

	@WampProcedure(PROCEDURE_LIST)
	public WampResult listProcedures(CallMessage callMessage) {
		return listProcedures(callMessage.getRealm());
	}

	public WampResult listTopics() {
		return listTopics((String) null);
	}

	@WampProcedure(TOPIC_LIST)
	public WampResult listTopics(CallMessage callMessage) {
		return listTopics(callMessage.getRealm());
	}

	@WampProcedure(PROCEDURE_DESCRIBE)
	public WampResult describeProcedure(CallMessage callMessage) {
		return new WampResult().add(describeProcedure(callMessage.getRealm(), stringArgument(callMessage, 0)));
	}

	@WampProcedure(TOPIC_DESCRIBE)
	public WampResult describeTopic(CallMessage callMessage) {
		return new WampResult().add(describeTopic(callMessage.getRealm(), stringArgument(callMessage, 0)));
	}

	public WampResult listErrors() {
		return WampResult.create(errorUris());
	}

	@WampProcedure(ERROR_LIST)
	public WampResult listErrors(@SuppressWarnings("unused") CallMessage callMessage) {
		return listErrors();
	}

	@WampProcedure(ERROR_DESCRIBE)
	public WampResult describeError(CallMessage callMessage) {
		return new WampResult().add(describeError(stringArgument(callMessage, 0)));
	}

	private WampResult listProcedures(@Nullable String realm) {
		return WampResult.create(resourceUris(this.procedureRegistry.listRegistrations(realm).values(),
				registrationIds -> registrationIds.stream()
					.map(this.procedureRegistry::getRegistration)
					.filter(Objects::nonNull)
					.map(ProcedureDetail::getProcedure)
					.toList()));
	}

	private WampResult listTopics(@Nullable String realm) {
		return WampResult.create(resourceUris(this.subscriptionRegistry.listSubscriptions(realm).values(),
				subscriptionIds -> subscriptionIds.stream()
					.map(this.subscriptionRegistry::getSubscription)
					.filter(Objects::nonNull)
					.map(SubscriptionDetail::getTopic)
					.toList()));
	}

	@Nullable private Map<String, Object> describeProcedure(@Nullable String realm, String procedureUri) {
		Map<MatchPolicy, List<Long>> registrationsByPolicy = this.procedureRegistry.listRegistrations(realm);
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			for (Long registrationId : Objects.requireNonNull(registrationsByPolicy.get(matchPolicy))) {
				ProcedureDetail detail = this.procedureRegistry.getRegistration(registrationId);
				if (detail != null && detail.getProcedure().equals(procedureUri)) {
					return toProcedureDescription(detail);
				}
			}
		}
		return null;
	}

	@Nullable private Map<String, Object> describeTopic(@Nullable String realm, String topicUri) {
		Map<MatchPolicy, List<Long>> subscriptionsByPolicy = this.subscriptionRegistry.listSubscriptions(realm);
		for (MatchPolicy matchPolicy : MatchPolicy.values()) {
			for (Long subscriptionId : Objects.requireNonNull(subscriptionsByPolicy.get(matchPolicy))) {
				SubscriptionDetail detail = this.subscriptionRegistry.getSubscription(subscriptionId);
				if (detail != null && detail.getTopic().equals(topicUri)) {
					return toTopicDescription(detail);
				}
			}
		}
		return null;
	}

	@Nullable private static Map<String, Object> describeError(String errorUri) {
		for (WampError error : WampError.values()) {
			if (error.getExternalValue().equals(errorUri) && isErrorUri(error)) {
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("uri", error.getExternalValue());
				result.put("name", error.name());
				result.put("type", "error");
				return result;
			}
		}
		return null;
	}

	private static Map<String, Object> toProcedureDescription(ProcedureDetail detail) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("uri", detail.getProcedure());
		result.put("registration", detail.getRegistrationId());
		result.put("realm", detail.getRealm());
		result.put("created", CREATED_FORMATTER.format(Instant.ofEpochMilli(detail.getCreated())));
		result.put("match", detail.getMatchPolicy().getExternalValue());
		result.put("invoke", detail.getInvocationPolicy().getExternalValue());
		result.put("type", "procedure");
		return result;
	}

	private static Map<String, Object> toTopicDescription(SubscriptionDetail detail) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("uri", detail.getTopic());
		result.put("subscription", detail.getId());
		result.put("realm", detail.getRealm());
		result.put("created", CREATED_FORMATTER.format(Instant.ofEpochMilli(detail.getCreatedTimeMillis())));
		result.put("match", detail.getMatchPolicy().getExternalValue());
		result.put("type", "topic");
		return result;
	}

	private static List<String> errorUris() {
		List<String> result = new ArrayList<>();
		for (WampError error : WampError.values()) {
			if (isErrorUri(error)) {
				result.add(error.getExternalValue());
			}
		}
		return result;
	}

	private static boolean isErrorUri(WampError error) {
		return error.getExternalValue().startsWith("wamp.error.");
	}

	private static List<String> resourceUris(Collection<List<Long>> idsByMatchPolicy,
			java.util.function.Function<List<Long>, List<String>> uriExtractor) {
		Set<String> uris = new LinkedHashSet<>();
		for (List<Long> ids : idsByMatchPolicy) {
			uris.addAll(uriExtractor.apply(ids));
		}
		return List.copyOf(uris);
	}

	private static String stringArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		return value.toString();
	}

	private static Object argument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			throw new IllegalArgumentException("missing call argument");
		}
		return arguments.get(index);
	}

}