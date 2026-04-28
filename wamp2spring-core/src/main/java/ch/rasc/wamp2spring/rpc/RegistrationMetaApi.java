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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.event.WampProcedureRegisteredEvent;
import ch.rasc.wamp2spring.event.WampProcedureUnregisteredEvent;
import ch.rasc.wamp2spring.event.WampRegistrationCreatedEvent;
import ch.rasc.wamp2spring.event.WampRegistrationDeletedEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.util.WampUriValidator;

public class RegistrationMetaApi {

	static final String LIST = "wamp.registration.list";

	static final String LOOKUP = "wamp.registration.lookup";

	static final String MATCH = "wamp.registration.match";

	static final String GET = "wamp.registration.get";

	static final String LIST_CALLEES = "wamp.registration.list_callees";

	static final String COUNT_CALLEES = "wamp.registration.count_callees";

	static final String ON_CREATE = "wamp.registration.on_create";

	static final String ON_REGISTER = "wamp.registration.on_register";

	static final String ON_UNREGISTER = "wamp.registration.on_unregister";

	static final String ON_DELETE = "wamp.registration.on_delete";

	private static final DateTimeFormatter CREATED_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
		.withZone(ZoneOffset.UTC);

	private final ProcedureRegistry procedureRegistry;

	private final WampPublisher wampPublisher;

	public RegistrationMetaApi(ProcedureRegistry procedureRegistry, WampPublisher wampPublisher) {
		this.procedureRegistry = procedureRegistry;
		this.wampPublisher = wampPublisher;
	}

	public WampResult list() {
		return list((String) null);
	}

	@WampProcedure(LIST)
	public WampResult list(CallMessage callMessage) {
		return list(callMessage.getRealm());
	}

	private WampResult list(@Nullable String realm) {
		Map<String, List<Long>> result = new LinkedHashMap<>();
		Map<MatchPolicy, List<Long>> registrations = this.procedureRegistry.listRegistrations(realm);
		result.put("exact", registrations.get(MatchPolicy.EXACT));
		result.put("prefix", registrations.get(MatchPolicy.PREFIX));
		result.put("wildcard", registrations.get(MatchPolicy.WILDCARD));
		return WampResult.create(result);
	}

	@WampProcedure(LOOKUP)
	public WampResult lookup(CallMessage callMessage) throws WampException {
		MatchPolicy matchPolicy = Objects.requireNonNullElse(matchPolicyOption(callMessage), MatchPolicy.EXACT);
		String procedure = stringArgument(callMessage, 0);
		WampUriValidator.validateProcedureRegistrationUri(procedure, matchPolicy);
		Long registrationId = this.procedureRegistry.lookupRegistration(callMessage.getRealm(), procedure, matchPolicy);
		return new WampResult().add(registrationId);
	}

	@WampProcedure(MATCH)
	public WampResult match(CallMessage callMessage) throws WampException {
		String procedure = stringArgument(callMessage, 0);
		WampUriValidator.validateCallUri(procedure);
		Long registrationId = this.procedureRegistry.matchRegistration(callMessage.getRealm(), procedure);
		return new WampResult().add(registrationId);
	}

	@WampProcedure(GET)
	public WampResult get(CallMessage callMessage) throws WampException {
		return WampResult.create(toMetaDetail(requireRegistration(callMessage, longArgument(callMessage, 0))));
	}

	@WampProcedure(LIST_CALLEES)
	public WampResult listCallees(CallMessage callMessage) throws WampException {
		long registrationId = longArgument(callMessage, 0);
		requireRegistration(callMessage, registrationId);
		return WampResult.create(this.procedureRegistry.listCallees(registrationId));
	}

	@WampProcedure(COUNT_CALLEES)
	public WampResult countCallees(CallMessage callMessage) throws WampException {
		ProcedureDetail detail = requireRegistration(callMessage, longArgument(callMessage, 0));
		Integer calleeCount = this.procedureRegistry.countCallees(detail.getRegistrationId());
		if (calleeCount == null) {
			throw noSuchRegistration();
		}
		return WampResult.create(calleeCount);
	}

	@EventListener
	public void onRegistrationCreated(WampRegistrationCreatedEvent event) {
		ProcedureDetail detail = this.procedureRegistry.getRegistration(event.getRegistrationId());
		if (detail != null) {
			publishEvent(event.getRealm(), ON_CREATE, event.getWampSessionId(), toMetaDetail(detail));
		}
	}

	@EventListener
	public void onProcedureRegistered(WampProcedureRegisteredEvent event) {
		publishEvent(event.getRealm(), ON_REGISTER, event.getWampSessionId(), event.getRegistrationId());
	}

	@EventListener
	public void onProcedureUnregistered(WampProcedureUnregisteredEvent event) {
		publishEvent(event.getRealm(), ON_UNREGISTER, event.getWampSessionId(), event.getRegistrationId());
	}

	@EventListener
	public void onRegistrationDeleted(WampRegistrationDeletedEvent event) {
		publishEvent(event.getRealm(), ON_DELETE, event.getWampSessionId(), event.getRegistrationId());
	}

	private void publishEvent(@Nullable String realm, String topic, @Nullable Object first, Object second) {
		List<Object> arguments = new ArrayList<>(2);
		arguments.add(first);
		arguments.add(second);
		PublishMessage publishMessage = this.wampPublisher.publishMessageBuilder(topic).arguments(arguments).build();
		publishMessage.setHeader(WampMessageHeader.WAMP_REALM, realm);
		this.wampPublisher.publish(publishMessage);
	}

	private ProcedureDetail requireRegistration(CallMessage callMessage, long registrationId) throws WampException {
		ProcedureDetail detail = this.procedureRegistry.getRegistration(registrationId);
		if (detail == null || !Objects.equals(callMessage.getRealm(), detail.getRealm())) {
			throw noSuchRegistration();
		}
		return detail;
	}

	private static WampException noSuchRegistration() {
		return new WampException.Builder().build(WampError.NO_SUCH_REGISTRATION.getExternalValue());
	}

	private static String stringArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		return value.toString();
	}

	private static long longArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static MatchPolicy matchPolicyOption(CallMessage callMessage) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() < 2 || !(arguments.get(1) instanceof Map<?, ?>)) {
			return MatchPolicy.EXACT;
		}

		String match = (String) ((Map<String, Object>) arguments.get(1)).get("match");
		if (match == null) {
			return MatchPolicy.EXACT;
		}
		MatchPolicy matchPolicy = MatchPolicy.fromExtValue(match);
		return matchPolicy != null ? matchPolicy : MatchPolicy.EXACT;
	}

	private static Object argument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			throw new IllegalArgumentException("missing call argument");
		}
		return arguments.get(index);
	}

	private static Map<String, Object> toMetaDetail(ProcedureDetail detail) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", detail.getRegistrationId());
		result.put("realm", detail.getRealm());
		result.put("created", CREATED_FORMATTER.format(Instant.ofEpochMilli(detail.getCreated())));
		result.put("uri", detail.getProcedure());
		result.put("match", detail.getMatchPolicy().getExternalValue());
		result.put("invoke", detail.getInvocationPolicy().getExternalValue());
		return result;
	}

}