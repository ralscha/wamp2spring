/**
 * Copyright 2017-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.rasc.wamp2spring.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.pubsub.PubSubMessageHandler;
import ch.rasc.wamp2spring.pubsub.SubscriptionRegistry;
import ch.rasc.wamp2spring.rpc.ProcedureRegistry;
import ch.rasc.wamp2spring.rpc.RpcMessageHandler;
import ch.rasc.wamp2spring.util.HandlerMethodService;

@Configuration
public class WampConfiguration implements ImportAware {

	@Nullable
	private ServletWebSocketHandlerRegistry handlerRegistry;

	@Nullable
	private ConversionService internalConversionService;

	private final List<WampConfigurer> configurers = new ArrayList<>();

	private final Features features = new Features();

	@Autowired(required = false)
	public void setConfigurers(List<WampConfigurer> configurers) {
		if (!CollectionUtils.isEmpty(configurers)) {
			this.configurers.addAll(configurers);

			configureFeatures(this.features);
			for (WampConfigurer wc : this.configurers) {
				wc.configureFeatures(this.features);
			}
		}
	}

	protected void configureFeatures(
			@SuppressWarnings({ "unused", "hiding" }) Features features) {
		// nothing here
	}

	@Autowired(required = false)
	private EventStore eventStore;

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributes = AnnotationAttributes.fromMap(importMetadata
				.getAnnotationAttributes(EnableWamp.class.getName(), false));
		Feature[] disableFeatures = (Feature[]) attributes.get("disable");
		if (disableFeatures != null) {
			for (Feature disableFeature : disableFeatures) {
				this.features.disable(disableFeature);
			}
		}
	}

	@Bean
	public SubProtocolWebSocketHandler subProtocolWebSocketHandler() {
		SubProtocolWebSocketHandler subProtocolWebSocketHandler = new SubProtocolWebSocketHandler(
				clientInboundChannel(), clientOutboundChannel());
		subProtocolWebSocketHandler.addProtocolHandler(wampSubProtocolHandler());
		return subProtocolWebSocketHandler;
	}

	@Bean
	public WampSubProtocolHandler wampSubProtocolHandler() {
		return new WampSubProtocolHandler(jsonJsonFactory(), msgpackJsonFactory(),
				cborJsonFactory(), smileJsonFactory(), clientInboundChannel(),
				this.features);
	}

	@Bean
	public JsonFactory jsonJsonFactory() {
		return new ObjectMapper().getFactory();
	}

	@Bean
	public JsonFactory msgpackJsonFactory() {
		return new ObjectMapper(new MessagePackFactory()).getFactory();
	}

	@Bean
	public JsonFactory cborJsonFactory() {
		return new ObjectMapper(new CBORFactory()).getFactory();
	}

	@Bean
	public JsonFactory smileJsonFactory() {
		return new ObjectMapper(new SmileFactory()).getFactory();
	}

	@Bean
	public HandlerMapping webSocketHandlerMapping() {
		ServletWebSocketHandlerRegistry registry = initHandlerRegistry();
		return registry.getHandlerMapping();
	}

	private ServletWebSocketHandlerRegistry initHandlerRegistry() {
		if (this.handlerRegistry == null) {
			this.handlerRegistry = new ServletWebSocketHandlerRegistry();
			registerWebSocketHandlers(this.handlerRegistry);
		}
		return this.handlerRegistry;
	}

	protected void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		SubProtocolWebSocketHandler subProtocolWebSocketHandler = subProtocolWebSocketHandler();

		Integer sendTimeLimit = getSendTimeLimit();
		if (sendTimeLimit != null) {
			subProtocolWebSocketHandler.setSendTimeLimit(sendTimeLimit);
		}

		Integer sendBufferSizeLimit = getSendBufferSizeLimit();
		if (sendBufferSizeLimit != null) {
			subProtocolWebSocketHandler.setSendBufferSizeLimit(sendBufferSizeLimit);
		}

		WebSocketHandler decoratedHandler = subProtocolWebSocketHandler;
		decoratedHandler = decorateWebSocketHandler(decoratedHandler);
		for (WampConfigurer wc : this.configurers) {
			decoratedHandler = wc.decorateWebSocketHandler(decoratedHandler);
		}

		WebSocketHandlerRegistration registration = registry.addHandler(decoratedHandler,
				getWebSocketHandlerPath());

		registration.setHandshakeHandler(getHandshakeHandler());

		configureWebSocketHandlerRegistration(registration);
		for (WampConfigurer wc : this.configurers) {
			wc.configureWebSocketHandlerRegistration(registration);
		}
	}

	protected WebSocketHandler decorateWebSocketHandler(WebSocketHandler handler) {
		return handler;
	}

	protected void configureWebSocketHandlerRegistration(
			@SuppressWarnings("unused") WebSocketHandlerRegistration registration) {
		// nothing here
	}

	protected HandshakeHandler getHandshakeHandler() {
		return new PreferBinaryHandshakeHandler();
	}

	protected String getWebSocketHandlerPath() {
		return "/wamp";
	}

	@Nullable
	protected Integer getSendTimeLimit() {
		return null;
	}

	@Nullable
	protected Integer getSendBufferSizeLimit() {
		return null;
	}

	@Bean
	public SubscribableChannel clientInboundChannel() {
		ExecutorSubscribableChannel executorSubscribableChannel = new ExecutorSubscribableChannel(
				clientInboundChannelExecutor());

		configureClientInboundChannel(executorSubscribableChannel);
		for (WampConfigurer wc : this.configurers) {
			wc.configureClientInboundChannel(executorSubscribableChannel);
		}

		return executorSubscribableChannel;
	}

	protected void configureClientInboundChannel(
			@SuppressWarnings("unused") ExecutorSubscribableChannel executorSubscribableChannel) {
		// nothing here
	}

	@Bean
	public Executor clientInboundChannelExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("wampClientInboundChannel-");
		executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
		executor.setMaxPoolSize(Integer.MAX_VALUE);
		executor.setKeepAliveSeconds(60);
		executor.setQueueCapacity(Integer.MAX_VALUE);
		executor.setAllowCoreThreadTimeOut(true);

		return executor;
	}

	@Bean
	public SubscribableChannel clientOutboundChannel() {
		return new ExecutorSubscribableChannel(clientOutboundChannelExecutor());
	}

	@Bean
	public Executor clientOutboundChannelExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("wampClientOutboundChannel-");
		executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
		executor.setMaxPoolSize(Integer.MAX_VALUE);
		executor.setKeepAliveSeconds(60);
		executor.setQueueCapacity(Integer.MAX_VALUE);
		executor.setAllowCoreThreadTimeOut(true);

		return executor;
	}

	/**
	 * Channel from the {@link WampPublisher} to the {@link PubSubMessageHandler}
	 */
	@Bean
	public SubscribableChannel brokerChannel() {
		return new ExecutorSubscribableChannel(brokerChannelExecutor());
	}

	/**
	 * Executor used by the {@link #brokerChannel()}. By default messages send through the
	 * brokerChannel are processed synchronously.
	 */
	@Nullable
	public Executor brokerChannelExecutor() {
		return null;
	}

	@Bean
	public MessageHandler pubSubMessageHandler(ApplicationContext applicationContext) {
		if (this.features.isEnabled(Feature.BROKER)) {
			PubSubMessageHandler pubSubMessageHandler = new PubSubMessageHandler(
					clientInboundChannel(), brokerChannel(), clientOutboundChannel(),
					subscriptionRegistry(), handlerMethodService(applicationContext),
					this.features, eventStore());
			return pubSubMessageHandler;
		}
		return new NoOpMessageHandler();
	}

	@Bean
	public SubscriptionRegistry subscriptionRegistry() {
		return new SubscriptionRegistry();
	}

	@Bean
	public MessageHandler rpcMessageHandler(ApplicationContext applicationContext) {
		if (this.features.isEnabled(Feature.DEALER)) {
			RpcMessageHandler rpcMessageHandler = new RpcMessageHandler(
					clientInboundChannel(), clientOutboundChannel(), procedureRegistry(),
					handlerMethodService(applicationContext), this.features);
			return rpcMessageHandler;
		}
		return new NoOpMessageHandler();
	}

	@Bean
	public HandlerMethodService handlerMethodService(
			ApplicationContext applicationContext) {
		List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();

		addArgumentResolvers(argumentResolvers);
		for (WampConfigurer wc : this.configurers) {
			wc.addArgumentResolvers(argumentResolvers);
		}

		return new HandlerMethodService(conversionService(), argumentResolvers,
				new ObjectMapper(), applicationContext);
	}

	protected void addArgumentResolvers(
			@SuppressWarnings("unused") List<HandlerMethodArgumentResolver> argumentResolvers) {
		// nothing here
	}

	protected ConversionService conversionService() {
		if (this.internalConversionService == null) {
			this.internalConversionService = new DefaultFormattingConversionService();
		}
		return this.internalConversionService;
	}

	protected EventStore eventStore() {
		if (this.eventStore == null) {
			this.eventStore = new MemoryEventStore();
		}
		return this.eventStore;
	}

	@Bean
	public ProcedureRegistry procedureRegistry() {
		return new ProcedureRegistry(this.features);
	}

	@Bean
	public WampPublisher wampEventPublisher() {
		return new WampPublisher(brokerChannel());
	}

}
