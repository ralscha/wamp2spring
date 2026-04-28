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
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;

import ch.rasc.wamp2spring.WampException;
import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.annotation.WampProcedure;
import ch.rasc.wamp2spring.event.WampDisconnectEvent;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.util.WampUriValidator;

public class SessionTestamentMetaApi {

	static final String ADD_TESTAMENT = "wamp.session.add_testament";

	static final String FLUSH_TESTAMENTS = "wamp.session.flush_testaments";

	private final ConcurrentMap<Long, SessionTestaments> testaments = new ConcurrentHashMap<>();

	private final WampPublisher wampPublisher;

	public SessionTestamentMetaApi(WampPublisher wampPublisher) {
		this.wampPublisher = wampPublisher;
	}

	@WampProcedure(ADD_TESTAMENT)
	public WampResult addTestament(CallMessage callMessage) throws WampException {
		long sessionId = callerSessionId(callMessage);
		String topic = stringArgument(callMessage, 0);
		validateUri(topic);

		List<Object> arguments = listArgument(callMessage, 1);
		Map<String, Object> argumentsKw = mapArgument(callMessage, 2);
		Map<String, Object> options = keywordArguments(callMessage);
		Scope scope = scope(options.get("scope"));
		Map<String, Object> publishOptions = nestedMap(options.get("publish_options"), "publish_options");

		PublishMessage.Builder builder = this.wampPublisher.publishMessageBuilder(topic);
		if (arguments != null) {
			builder.arguments(arguments);
		}
		if (argumentsKw != null) {
			builder.arguments(argumentsKw);
		}
		applyPublishOptions(builder, publishOptions);

		this.testaments.computeIfAbsent(sessionId, key -> new SessionTestaments()).add(scope, builder.build());
		return new WampResult();
	}

	@WampProcedure(FLUSH_TESTAMENTS)
	public WampResult flushTestaments(CallMessage callMessage) {
		long sessionId = callerSessionId(callMessage);
		Scope scope = scope(keywordArguments(callMessage).get("scope"));

		SessionTestaments sessionTestaments = this.testaments.get(sessionId);
		if (sessionTestaments != null) {
			sessionTestaments.flush(scope);
			if (sessionTestaments.isEmpty()) {
				this.testaments.remove(sessionId, sessionTestaments);
			}
		}
		return new WampResult();
	}

	@EventListener
	public void onSessionLeft(WampDisconnectEvent event) {
		Long sessionId = event.getWampSessionId();
		if (sessionId == null) {
			return;
		}

		SessionTestaments sessionTestaments = this.testaments.remove(sessionId);
		if (sessionTestaments == null) {
			return;
		}

		publishAll(sessionTestaments.drain(Scope.DETACHED));
		publishAll(sessionTestaments.drain(Scope.DESTROYED));
	}

	private void publishAll(List<PublishMessage> publishMessages) {
		for (PublishMessage publishMessage : publishMessages) {
			this.wampPublisher.publish(publishMessage);
		}
	}

	private static void applyPublishOptions(PublishMessage.Builder builder,
			@Nullable Map<String, Object> publishOptions) {
		if (publishOptions == null || publishOptions.isEmpty()) {
			return;
		}

		if (publishOptions.containsKey("exclude_me") && !booleanOption(publishOptions.get("exclude_me"), true)) {
			builder.notExcludeMe();
		}
		if (booleanOption(publishOptions.get("disclose_me"), false)) {
			builder.discloseMe();
		}
		if (booleanOption(publishOptions.get("retain"), false)) {
			builder.retain();
		}

		addNumberOptions(builder, nestedCollection(publishOptions.get("exclude"), "exclude"), true);
		addNumberOptions(builder, nestedCollection(publishOptions.get("eligible"), "eligible"), false);
		addStringOptions(builder, nestedCollection(publishOptions.get("exclude_authid"), "exclude_authid"),
				OptionType.EXCLUDE_AUTH_ID);
		addStringOptions(builder, nestedCollection(publishOptions.get("eligible_authid"), "eligible_authid"),
				OptionType.ELIGIBLE_AUTH_ID);
		addStringOptions(builder, nestedCollection(publishOptions.get("exclude_authrole"), "exclude_authrole"),
				OptionType.EXCLUDE_AUTH_ROLE);
		addStringOptions(builder, nestedCollection(publishOptions.get("eligible_authrole"), "eligible_authrole"),
				OptionType.ELIGIBLE_AUTH_ROLE);
	}

	private static void addNumberOptions(PublishMessage.Builder builder, @Nullable Collection<?> values,
			boolean exclude) {
		if (values == null) {
			return;
		}
		for (Object value : values) {
			if (!(value instanceof Number number)) {
				throw new IllegalArgumentException("expected numeric publish option entry");
			}
			if (exclude) {
				builder.addExclude(number);
			}
			else {
				builder.addEligible(number);
			}
		}
	}

	private static void addStringOptions(PublishMessage.Builder builder, @Nullable Collection<?> values,
			OptionType optionType) {
		if (values == null) {
			return;
		}

		List<String> stringValues = new ArrayList<>(values.size());
		for (Object value : values) {
			if (value == null) {
				throw new IllegalArgumentException("expected string publish option entry");
			}
			stringValues.add(value.toString());
		}

		switch (optionType) {
			case EXCLUDE_AUTH_ID -> builder.excludeAuthIds(stringValues);
			case ELIGIBLE_AUTH_ID -> builder.eligibleAuthIds(stringValues);
			case EXCLUDE_AUTH_ROLE -> builder.excludeAuthRoles(stringValues);
			case ELIGIBLE_AUTH_ROLE -> builder.eligibleAuthRoles(stringValues);
		}
	}

	private static boolean booleanOption(@Nullable Object value, boolean defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		return Boolean.parseBoolean(value.toString());
	}

	private static long callerSessionId(CallMessage callMessage) {
		Long sessionId = callMessage.getWampSessionId();
		if (sessionId == null) {
			throw new IllegalArgumentException("missing caller session id");
		}
		return sessionId;
	}

	private static String stringArgument(CallMessage callMessage, int index) {
		Object value = argument(callMessage, index);
		if (value == null) {
			throw new IllegalArgumentException("missing call argument");
		}
		return value.toString();
	}

	@SuppressWarnings("unchecked")
	@Nullable private static List<Object> listArgument(CallMessage callMessage, int index) {
		Object value = optionalArgument(callMessage, index);
		if (value == null) {
			return null;
		}
		if (value instanceof List<?> list) {
			return (List<Object>) list;
		}
		throw new IllegalArgumentException("expected list call argument");
	}

	@SuppressWarnings("unchecked")
	@Nullable private static Map<String, Object> mapArgument(CallMessage callMessage, int index) {
		Object value = optionalArgument(callMessage, index);
		if (value == null) {
			return null;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> casted = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				casted.put(entry.getKey().toString(), entry.getValue());
			}
			return casted;
		}
		throw new IllegalArgumentException("expected map call argument");
	}

	private static Object argument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			throw new IllegalArgumentException("missing call argument");
		}
		return arguments.get(index);
	}

	@Nullable private static Object optionalArgument(CallMessage callMessage, int index) {
		List<Object> arguments = callMessage.getArguments();
		if (arguments == null || arguments.size() <= index) {
			return null;
		}
		return arguments.get(index);
	}

	private static Map<String, Object> keywordArguments(CallMessage callMessage) {
		Map<String, Object> argumentsKw = callMessage.getArgumentsKw();
		return argumentsKw != null ? argumentsKw : Collections.emptyMap();
	}

	@SuppressWarnings("unchecked")
	@Nullable private static Map<String, Object> nestedMap(@Nullable Object value, String name) {
		if (value == null) {
			return null;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				result.put(entry.getKey().toString(), entry.getValue());
			}
			return result;
		}
		throw new IllegalArgumentException("expected map keyword argument for " + name);
	}

	@Nullable private static Collection<?> nestedCollection(@Nullable Object value, String name) {
		if (value == null) {
			return null;
		}
		if (value instanceof Collection<?> collection) {
			return collection;
		}
		throw new IllegalArgumentException("expected list keyword argument for " + name);
	}

	private static Scope scope(@Nullable Object value) {
		if (value == null) {
			return Scope.DESTROYED;
		}
		return Scope.from(value.toString());
	}

	private static void validateUri(String uri) throws WampException {
		WampUriValidator.validatePublishTopic(uri);
	}

	private enum Scope {

		DETACHED("detached"), DESTROYED("destroyed");

		private final String externalValue;

		Scope(String externalValue) {
			this.externalValue = externalValue;
		}

		static Scope from(String externalValue) {
			for (Scope value : values()) {
				if (value.externalValue.equals(externalValue)) {
					return value;
				}
			}
			throw new IllegalArgumentException("unsupported testament scope: " + externalValue);
		}

	}

	private enum OptionType {

		EXCLUDE_AUTH_ID, ELIGIBLE_AUTH_ID, EXCLUDE_AUTH_ROLE, ELIGIBLE_AUTH_ROLE

	}

	private static final class SessionTestaments {

		private final EnumMap<Scope, List<PublishMessage>> values = new EnumMap<>(Scope.class);

		private SessionTestaments() {
			for (Scope scope : Scope.values()) {
				this.values.put(scope, new ArrayList<>());
			}
		}

		synchronized void add(Scope scope, PublishMessage publishMessage) {
			valuesFor(scope).add(publishMessage);
		}

		synchronized void flush(Scope scope) {
			valuesFor(scope).clear();
		}

		synchronized List<PublishMessage> drain(Scope scope) {
			List<PublishMessage> scopedValues = valuesFor(scope);
			List<PublishMessage> drained = new ArrayList<>(scopedValues);
			scopedValues.clear();
			return drained;
		}

		synchronized boolean isEmpty() {
			return this.values.values().stream().allMatch(List::isEmpty);
		}

		private List<PublishMessage> valuesFor(Scope scope) {
			return Objects.requireNonNull(this.values.get(scope));
		}

	}

}