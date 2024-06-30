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
package ch.rasc.wamp2spring.util;

import java.util.List;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.annotation.support.HeaderMethodArgumentResolver;
import org.springframework.messaging.handler.annotation.support.HeadersMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.config.PrincipalMethodArgumentResolver;
import ch.rasc.wamp2spring.config.WampMessageMethodArgumentResolver;
import ch.rasc.wamp2spring.config.WampSessionIdMethodArgumentResolver;
import ch.rasc.wamp2spring.message.CallMessage;
import ch.rasc.wamp2spring.message.EventMessage;

public class HandlerMethodService {

	private final ParameterNameDiscoverer parameterNameDiscoverer;

	private final ConversionService conversionService;

	private final HandlerMethodArgumentResolverComposite argumentResolvers;

	private final ObjectMapper objectMapper;

	public HandlerMethodService(ConversionService conversionService,
			List<HandlerMethodArgumentResolver> customArgumentResolvers,
			ObjectMapper objectMapper, ApplicationContext applicationContext) {
		this.conversionService = conversionService;
		this.parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

		this.argumentResolvers = new HandlerMethodArgumentResolverComposite();

		ConfigurableBeanFactory beanFactory = ClassUtils.isAssignableValue(
				ConfigurableApplicationContext.class, applicationContext)
						? ((ConfigurableApplicationContext) applicationContext)
								.getBeanFactory()
						: null;

		this.argumentResolvers.addResolver(
				new HeaderMethodArgumentResolver(this.conversionService, beanFactory));
		this.argumentResolvers.addResolver(new HeadersMethodArgumentResolver());
		this.argumentResolvers.addResolver(new WampMessageMethodArgumentResolver());
		this.argumentResolvers.addResolver(new PrincipalMethodArgumentResolver());
		this.argumentResolvers.addResolver(new WampSessionIdMethodArgumentResolver());
		this.argumentResolvers.addResolvers(customArgumentResolvers);

		this.objectMapper = objectMapper;
	}

	@Nullable
	public Object invoke(CallMessage callMessage, InvocableHandlerMethod handlerMethod)
			throws Exception {
		setHelpers(handlerMethod);
		return handlerMethod.invoke(callMessage, callMessage.getArguments(),
				callMessage.getArgumentsKw());
	}

	@Nullable
	public Object invoke(EventMessage eventMessage, InvocableHandlerMethod handlerMethod)
			throws Exception {
		setHelpers(handlerMethod);
		return handlerMethod.invoke(eventMessage, eventMessage.getArguments(),
				eventMessage.getArgumentsKw());
	}

	private void setHelpers(InvocableHandlerMethod handlerMethod) {
		handlerMethod.setArgumentResolvers(this.argumentResolvers);
		handlerMethod.setConversionService(this.conversionService);
		handlerMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		handlerMethod.setObjectMapper(this.objectMapper);
	}

}
