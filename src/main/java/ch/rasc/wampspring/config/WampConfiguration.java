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
package ch.rasc.wampspring.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
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

import ch.rasc.wampspring.WampPublisher;
import ch.rasc.wampspring.pubsub.PubSubMessageHandler;
import ch.rasc.wampspring.pubsub.SubscriptionRegistry;
import ch.rasc.wampspring.rpc.ProcedureRegistry;
import ch.rasc.wampspring.rpc.RpcMessageHandler;
import ch.rasc.wampspring.util.HandlerMethodService;

@Configuration
public class WampConfiguration {

	@Nullable
	private ServletWebSocketHandlerRegistry handlerRegistry;

	@Nullable
	private ConversionService internalConversionService;

	private final List<WampConfigurer> configurers = new ArrayList<>();

	@Autowired(required = false)
	public void setConfigurers(List<WampConfigurer> configurers) {
		if (!CollectionUtils.isEmpty(configurers)) {
			this.configurers.addAll(configurers);
		}
	}

	@Bean
	public WebSocketHandler subProtocolWebSocketHandler() {
		SubProtocolWebSocketHandler subProtocolWebSocketHandler = new SubProtocolWebSocketHandler(
				clientInboundChannel(), clientOutboundChannel());
		subProtocolWebSocketHandler.addProtocolHandler(wampSubProtocolHandler());
		return subProtocolWebSocketHandler;
	}

	@Bean
	public WampSubProtocolHandler wampSubProtocolHandler() {
		return new WampSubProtocolHandler(jsonJsonFactory(), msgpackJsonFactory(),
				cborJsonFactory(), clientInboundChannel());
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
		WebSocketHandlerRegistration registration = registry
				.addHandler(subProtocolWebSocketHandler(), getWebSocketHandlerPath());

		registration.setHandshakeHandler(getHandshakeHandler());

		for (WampConfigurer wc : this.configurers) {
			wc.configureWebSocketHandlerRegistration(registration);
		}
	}

	protected HandshakeHandler getHandshakeHandler() {
		return new PreferMsgpackHandshakeHandler();
	}

	protected String getWebSocketHandlerPath() {
		return "/wamp";
	}

	@Bean
	public SubscribableChannel clientInboundChannel() {
		ExecutorSubscribableChannel executorSubscribableChannel = new ExecutorSubscribableChannel(
				clientInboundChannelExecutor());

		for (WampConfigurer wc : this.configurers) {
			wc.configureClientInboundChannel(executorSubscribableChannel);
		}

		return executorSubscribableChannel;
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

	@Bean
	public MessageHandler pubSubMessageHandler(ApplicationContext applicationContext) {
		PubSubMessageHandler pubSubMessageHandler = new PubSubMessageHandler(
				clientInboundChannel(), clientOutboundChannel(), subscriptionRegistry(),
				handlerMethodService(applicationContext));
		return pubSubMessageHandler;
	}

	@Bean
	public SubscriptionRegistry subscriptionRegistry() {
		return new SubscriptionRegistry();
	}

	@Bean
	public MessageHandler rpcMessageHandler(ApplicationContext applicationContext) {
		RpcMessageHandler rpcMessageHandler = new RpcMessageHandler(
				clientInboundChannel(), clientOutboundChannel(), procedureRegistry(),
				handlerMethodService(applicationContext));
		return rpcMessageHandler;
	}

	@Bean
	public HandlerMethodService handlerMethodService(
			ApplicationContext applicationContext) {
		List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
		for (WampConfigurer wc : this.configurers) {
			wc.addArgumentResolvers(argumentResolvers);
		}

		return new HandlerMethodService(conversionService(), argumentResolvers,
				new ObjectMapper(), applicationContext);
	}

	public void addArgumentResolvers(
			List<HandlerMethodArgumentResolver> argumentResolvers) {
		for (WampConfigurer wc : this.configurers) {
			wc.addArgumentResolvers(argumentResolvers);
		}
	}

	protected ConversionService conversionService() {
		if (this.internalConversionService == null) {
			this.internalConversionService = new DefaultFormattingConversionService();
		}
		return this.internalConversionService;
	}

	@Bean
	public ProcedureRegistry procedureRegistry() {
		return new ProcedureRegistry();
	}

	@Bean
	public WampPublisher wampEventPublisher() {
		return new WampPublisher(clientInboundChannel());
	}

}
