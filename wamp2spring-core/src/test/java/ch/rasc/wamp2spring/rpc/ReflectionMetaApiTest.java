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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.config.Features;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.WampMessageHeader;
import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;

public class ReflectionMetaApiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void exposesProcedureTopicAndErrorReflection() {
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(new Features());
		SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();

		register(procedureRegistry, registerMessage(1L, 101L, "callee-1", "com.myapp.worker"));
		register(procedureRegistry,
				registerMessage(2L, 202L, "callee-2", "com.myapp.worker.prefix", MatchPolicy.PREFIX));
		subscribe(subscriptionRegistry, subscribeMessage(3L, 303L, "subscriber-1", "com.myapp.orders"));
		subscribe(subscriptionRegistry,
				subscribeMessage(4L, 404L, "subscriber-2", "com.myapp.orders.prefix", MatchPolicy.PREFIX));

		ReflectionMetaApi api = new ReflectionMetaApi(procedureRegistry, subscriptionRegistry);

		WampResult procedureList = api.listProcedures();
		WampResult topicList = api.listTopics();
		WampResult errorList = api.listErrors();
		WampResult procedureDescribe = api
			.describeProcedure(new CallMessage(10L, ReflectionMetaApi.PROCEDURE_DESCRIBE, List.of("com.myapp.worker")));
		WampResult topicDescribe = api
			.describeTopic(new CallMessage(11L, ReflectionMetaApi.TOPIC_DESCRIBE, List.of("com.myapp.orders")));
		WampResult errorDescribe = api.describeError(new CallMessage(12L, ReflectionMetaApi.ERROR_DESCRIBE,
				List.of(WampError.NO_SUCH_PROCEDURE.getExternalValue())));
		WampResult missingDescribe = api.describeProcedure(
				new CallMessage(13L, ReflectionMetaApi.PROCEDURE_DESCRIBE, List.of("com.myapp.missing")));

		assertThat((List<String>) Objects.requireNonNull(procedureList.getResults()).get(0))
			.containsExactly("com.myapp.worker", "com.myapp.worker.prefix");
		assertThat((List<String>) Objects.requireNonNull(topicList.getResults()).get(0))
			.containsExactly("com.myapp.orders", "com.myapp.orders.prefix");
		assertThat((List<String>) Objects.requireNonNull(errorList.getResults()).get(0))
			.contains(WampError.NO_SUCH_PROCEDURE.getExternalValue(),
					WampError.FEATURE_NOT_SUPPORTED.getExternalValue())
			.doesNotContain(WampError.GOODBYE_AND_OUT.getExternalValue());

		Map<String, Object> procedureDescription = (Map<String, Object>) Objects
			.requireNonNull(procedureDescribe.getResults())
			.get(0);
		assertThat(procedureDescription).containsEntry("uri", "com.myapp.worker");
		assertThat(procedureDescription).containsEntry("type", "procedure");
		assertThat(procedureDescription).containsEntry("match", MatchPolicy.EXACT.getExternalValue());
		assertThat(procedureDescription.get("invoke")).isEqualTo(InvocationPolicy.SINGLE.getExternalValue());

		Map<String, Object> topicDescription = (Map<String, Object>) Objects.requireNonNull(topicDescribe.getResults())
			.get(0);
		assertThat(topicDescription).containsEntry("uri", "com.myapp.orders");
		assertThat(topicDescription).containsEntry("type", "topic");
		assertThat(topicDescription).containsEntry("match", MatchPolicy.EXACT.getExternalValue());

		Map<String, Object> errorDescription = (Map<String, Object>) Objects.requireNonNull(errorDescribe.getResults())
			.get(0);
		assertThat(errorDescription).containsEntry("uri", WampError.NO_SUCH_PROCEDURE.getExternalValue());
		assertThat(errorDescription).containsEntry("type", "error");
		assertThat(errorDescription).containsEntry("name", WampError.NO_SUCH_PROCEDURE.name());

		assertThat(Objects.requireNonNull(missingDescribe.getResults())).containsExactly((Object) null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void ignoresRealmWhenReflectingProceduresAndTopics() {
		ProcedureRegistry procedureRegistry = new ProcedureRegistry(new Features());
		SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();

		RegisterMessage realmOneProcedure = registerMessage(1L, 101L, "callee-1", "com.myapp.realm.one");
		register(procedureRegistry, realmOneProcedure);

		RegisterMessage realmTwoProcedure = registerMessage(2L, 202L, "callee-2", "com.myapp.realm.two");
		register(procedureRegistry, realmTwoProcedure);

		SubscribeMessage realmOneTopic = subscribeMessage(3L, 303L, "subscriber-1", "com.myapp.topic.one");
		subscribe(subscriptionRegistry, realmOneTopic);

		SubscribeMessage realmTwoTopic = subscribeMessage(4L, 404L, "subscriber-2", "com.myapp.topic.two");
		subscribe(subscriptionRegistry, realmTwoTopic);

		ReflectionMetaApi api = new ReflectionMetaApi(procedureRegistry, subscriptionRegistry);

		assertThat((List<String>) Objects.requireNonNull(api.listProcedures().getResults()).get(0))
			.containsExactly("com.myapp.realm.one", "com.myapp.realm.two");
		assertThat((List<String>) Objects.requireNonNull(api.listTopics().getResults()).get(0))
			.containsExactlyInAnyOrder("com.myapp.topic.one", "com.myapp.topic.two");
	}

	private static void register(ProcedureRegistry procedureRegistry, RegisterMessage message) {
		procedureRegistry.register(message);
	}

	private static RegisterMessage registerMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String procedure) {
		return registerMessage(requestId, wampSessionId, webSocketSessionId, procedure, MatchPolicy.EXACT);
	}

	private static RegisterMessage registerMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String procedure, MatchPolicy matchPolicy) {
		RegisterMessage message = new RegisterMessage(requestId, procedure, false, matchPolicy,
				InvocationPolicy.SINGLE);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

	private static long subscribe(SubscriptionRegistry subscriptionRegistry, SubscribeMessage message) {
		try {
			Method subscribeMethod = SubscriptionRegistry.class.getDeclaredMethod("subscribe", SubscribeMessage.class);
			subscribeMethod.setAccessible(true);
			Object subscribeResult = subscribeMethod.invoke(subscriptionRegistry, message);

			Method getSubscriptionMethod = subscribeResult.getClass().getDeclaredMethod("getSubscription");
			getSubscriptionMethod.setAccessible(true);
			Object subscription = getSubscriptionMethod.invoke(subscribeResult);

			Method getSubscriptionIdMethod = subscription.getClass().getDeclaredMethod("getSubscriptionId");
			getSubscriptionIdMethod.setAccessible(true);
			return (Long) getSubscriptionIdMethod.invoke(subscription);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static SubscribeMessage subscribeMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String topic) {
		return subscribeMessage(requestId, wampSessionId, webSocketSessionId, topic, MatchPolicy.EXACT);
	}

	private static SubscribeMessage subscribeMessage(long requestId, long wampSessionId, String webSocketSessionId,
			String topic, MatchPolicy matchPolicy) {
		SubscribeMessage message = new SubscribeMessage(requestId, topic, matchPolicy, false);
		message.setHeader(WampMessageHeader.WAMP_SESSION_ID, wampSessionId);
		message.setHeader(WampMessageHeader.WEBSOCKET_SESSION_ID, webSocketSessionId);
		return message;
	}

}