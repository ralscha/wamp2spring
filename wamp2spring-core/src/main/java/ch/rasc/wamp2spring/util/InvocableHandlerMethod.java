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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import ch.rasc.wamp2spring.message.WampMessage;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.TypeBindings;
import tools.jackson.databind.type.TypeFactory;

public class InvocableHandlerMethod extends HandlerMethod {

	@Nullable private HandlerMethodArgumentResolverComposite argumentResolvers;

	@Nullable private ParameterNameDiscoverer parameterNameDiscoverer;

	@Nullable private ConversionService conversionService;

	@Nullable private ObjectMapper objectMapper;

	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	public void setArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
		this.argumentResolvers = argumentResolvers;
	}

	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Invoke the method after resolving its argument values in the context of the given
	 * message.
	 * <p>
	 * Argument values are commonly resolved through
	 * {@link HandlerMethodArgumentResolver}s. The {@code providedArgs} parameter however
	 * may supply argument values to be used directly, i.e. without argument resolution.
	 * @param message the current message being processed
	 * @param arguments positional arguments supplied with the WAMP message
	 * @param argumentsKw keyword arguments supplied with the WAMP message
	 * @return the raw value returned by the invoked method
	 * @throws Exception raised if no suitable argument resolver can be found, or if the
	 * method raised an exception
	 */
	@Nullable public Object invoke(WampMessage message, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) throws Exception {
		Object[] args = getMethodArgumentValues(message, arguments, argumentsKw);
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Invoking '" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType())
					+ "' with arguments " + Arrays.toString(args));
		}
		Object returnValue = doInvoke(args);
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Method [" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType())
					+ "] returned [" + returnValue + "]");
		}
		return returnValue;
	}

	private Object[] getMethodArgumentValues(WampMessage message, @Nullable List<Object> arguments,
			@Nullable Map<String, Object> argumentsKw) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		Object[] args = new Object[parameters.length];
		HandlerMethodArgumentResolverComposite argumentResolvers = getArgumentResolvers();
		int argIndex = 0;
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
			@Nullable Object resolvedArg = null;

			if (argumentResolvers.supportsParameter(parameter)) {
				try {
					resolvedArg = argumentResolvers.resolveArgument(parameter, message);
				}
				catch (Exception ex) {
					if (this.logger.isDebugEnabled()) {
						this.logger.debug(getArgumentResolutionErrorMessage("Failed to resolve", i), ex);
					}
					throw ex;
				}
			}
			else if (arguments != null && arguments.size() > argIndex) {
				resolvedArg = convert(parameter, arguments.get(argIndex));
				if (resolvedArg != null) {
					argIndex++;
				}
			}

			if (resolvedArg == null && argumentsKw != null) {
				String paramName = parameter.getParameterName();
				if (paramName != null) {
					Object arg = argumentsKw.get(paramName);
					if (arg != null) {
						resolvedArg = convert(parameter, arg);
					}
				}
			}

			if (resolvedArg == null) {
				throw new MethodArgumentResolutionException(message, parameter,
						getArgumentResolutionErrorMessage("No suitable resolver for", i));
			}

			args[i] = resolvedArg;
		}

		return args;
	}

	@Nullable public Object convert(MethodParameter parameter, @Nullable Object argument) {
		if (argument == null) {
			if (parameter.getParameterType().equals(Optional.class)) {
				return Optional.empty();
			}

			return null;
		}

		Class<?> sourceClass = argument.getClass();
		Class<?> targetClass = parameter.getParameterType();

		TypeDescriptor td = new TypeDescriptor(parameter);

		if (targetClass.isAssignableFrom(sourceClass)) {
			return convertListElements(td, argument);
		}

		ConversionService conversionService = getConversionService();
		if (conversionService.canConvert(sourceClass, targetClass)) {
			try {
				return convertListElements(td, conversionService.convert(argument, targetClass));
			}
			catch (Exception e) {
				// ignore this exception for collections and arrays.
				// try to convert the value with Jackson

				TypeFactory typeFactory = getObjectMapper().getTypeFactory();
				if (td.getElementTypeDescriptor() != null) {
					if (td.isCollection()) {
						JavaType elemType = typeFactory.constructType(td.getElementTypeDescriptor().getType());
						TypeVariable<?>[] vars = targetClass.getTypeParameters();
						TypeBindings bindings;
						if (vars == null || vars.length != 1) {
							bindings = TypeBindings.emptyBindings();
						}
						else {
							bindings = TypeBindings.create(targetClass, elemType);
						}
						JavaType superClass = null;
						Class<?> parent = targetClass.getSuperclass();
						if (parent != null) {
							superClass = TypeFactory.unknownType();
						}

						JavaType type = CollectionType.construct(targetClass, bindings, superClass, null, elemType);
						return getObjectMapper().convertValue(argument, type);
					}
					if (td.isArray()) {
						JavaType type = typeFactory.constructArrayType(td.getElementTypeDescriptor().getType());
						return getObjectMapper().convertValue(argument, type);
					}
				}

				throw e;
			}
		}
		return getObjectMapper().convertValue(argument, targetClass);
	}

	@SuppressWarnings("unchecked")
	@Nullable private Object convertListElements(TypeDescriptor td, @Nullable Object convertedValue) {
		if (convertedValue != null && List.class.isAssignableFrom(convertedValue.getClass()) && td.isCollection()
				&& td.getElementTypeDescriptor() != null) {
			Class<?> elementType = td.getElementTypeDescriptor().getType();

			Collection<Object> convertedList = new ArrayList<>();
			for (Object record : (List<Object>) convertedValue) {
				Object convertedObject = getObjectMapper().convertValue(record, elementType);
				convertedList.add(convertedObject);
			}
			return convertedList;

		}
		return convertedValue;
	}

	private String getArgumentResolutionErrorMessage(String text, int index) {
		Class<?> paramType = getMethodParameters()[index].getParameterType();
		return text + " argument " + index + " of type '" + paramType.getName() + "'";
	}

	/**
	 * Invoke the handler method with the given argument values.
	 */
	@Nullable private Object doInvoke(Object[] args) throws Exception {
		ReflectionUtils.makeAccessible(getBridgedMethod());
		try {
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(getBridgedMethod(), getBean(), args);
			String text = ex.getMessage() != null ? ex.getMessage() : "Illegal argument";
			throw new IllegalStateException(getInvocationErrorMessage(text, args), ex);
		}
		catch (InvocationTargetException ex) {
			// Unwrap for HandlerExceptionResolvers ...
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (targetException instanceof Error error) {
				throw error;
			}
			if (targetException instanceof Exception exception) {
				throw exception;
			}
			String text = getInvocationErrorMessage("Failed to invoke handler method", args);
			throw new IllegalStateException(text, targetException);
		}
	}

	/**
	 * Assert that the target bean class is an instance of the class where the given
	 * method is declared. In some cases the actual endpoint instance at request-
	 * processing time may be a JDK dynamic proxy (lazy initialization, prototype beans,
	 * and others). Endpoint classes that require proxying should prefer class-based proxy
	 * mechanisms.
	 */
	@Override
	protected void assertTargetBean(Method method, Object targetBean, @Nullable Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String text = "The mapped handler method class '" + methodDeclaringClass.getName()
					+ "' is not an instance of the actual endpoint bean class '" + targetBeanClass.getName()
					+ "'. If the endpoint requires proxying "
					+ "(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(text, args));
		}
	}

	private String getInvocationErrorMessage(String text, @Nullable Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(text));
		sb.append("Resolved arguments: \n");
		if (resolvedArgs == null) {
			sb.append("[none]\n");
			return sb.toString();
		}
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}

	/**
	 * Adds HandlerMethod details such as the bean type and method signature to the
	 * message.
	 * @param text error message to append the HandlerMethod details to
	 */
	private String getDetailedErrorMessage(String text) {
		StringBuilder sb = new StringBuilder(text).append("\n");
		sb.append("HandlerMethod details: \n");
		sb.append("Endpoint [").append(getBeanType().getName()).append("]\n");
		sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
		return sb.toString();
	}

	private HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return Objects.requireNonNull(this.argumentResolvers);
	}

	private ConversionService getConversionService() {
		return Objects.requireNonNull(this.conversionService);
	}

	private ObjectMapper getObjectMapper() {
		return Objects.requireNonNull(this.objectMapper);
	}

}
