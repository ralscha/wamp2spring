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
import java.util.Optional;

import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.fasterxml.jackson.databind.type.TypeFactory;

import ch.rasc.wamp2spring.message.WampMessage;

public class InvocableHandlerMethod extends HandlerMethod {

	private HandlerMethodArgumentResolverComposite argumentResolvers;

	private ParameterNameDiscoverer parameterNameDiscoverer;

	private ConversionService conversionService;

	private ObjectMapper objectMapper;

	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	public void setArgumentResolvers(
			HandlerMethodArgumentResolverComposite argumentResolvers) {
		this.argumentResolvers = argumentResolvers;
	}

	public void setParameterNameDiscoverer(
			ParameterNameDiscoverer parameterNameDiscoverer) {
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
	 * @param arguments
	 * @param argumentsKw
	 * @return the raw value returned by the invoked method
	 * @exception Exception raised if no suitable argument resolver can be found, or if
	 * the method raised an exception
	 */
	@Nullable
	public Object invoke(WampMessage message, List<Object> arguments,
			Map<String, Object> argumentsKw) throws Exception {
		Object[] args = getMethodArgumentValues(message, arguments, argumentsKw);
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Invoking '"
					+ ClassUtils.getQualifiedMethodName(getMethod(), getBeanType())
					+ "' with arguments " + Arrays.toString(args));
		}
		Object returnValue = doInvoke(args);
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Method ["
					+ ClassUtils.getQualifiedMethodName(getMethod(), getBeanType())
					+ "] returned [" + returnValue + "]");
		}
		return returnValue;
	}

	private Object[] getMethodArgumentValues(WampMessage message, List<Object> arguments,
			Map<String, Object> argumentsKw) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		Object[] args = new Object[parameters.length];
		int argIndex = 0;
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);

			if (this.argumentResolvers.supportsParameter(parameter)) {
				try {
					args[i] = this.argumentResolvers.resolveArgument(parameter, message);
					continue;
				}
				catch (Exception ex) {
					if (this.logger.isDebugEnabled()) {
						this.logger.debug(
								getArgumentResolutionErrorMessage("Failed to resolve", i),
								ex);
					}
					throw ex;
				}
			}

			if (arguments != null && arguments.size() > argIndex) {
				args[i] = convert(parameter, arguments.get(argIndex));
				if (args[i] != null) {
					argIndex++;
					continue;
				}
			}

			if (argumentsKw != null) {
				String paramName = parameter.getParameterName();
				if (paramName != null) {
					Object arg = argumentsKw.get(paramName);
					if (arg != null) {
						args[i] = convert(parameter, arg);
						continue;
					}
				}
			}

			if (args[i] == null) {
				throw new MethodArgumentResolutionException(message, parameter,
						getArgumentResolutionErrorMessage("No suitable resolver for", i));
			}
		}

		return args;
	}

	@Nullable
	public Object convert(MethodParameter parameter, Object argument) {
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

		if (this.conversionService.canConvert(sourceClass, targetClass)) {
			try {
				return convertListElements(td,
						this.conversionService.convert(argument, targetClass));
			}
			catch (Exception e) {
				// ignore this exception for collections and arrays.
				// try to convert the value with Jackson

				TypeFactory typeFactory = this.objectMapper.getTypeFactory();
				if (td.getElementTypeDescriptor() != null) {
					if (td.isCollection()) {
						JavaType elemType = typeFactory
								.constructType(td.getElementTypeDescriptor().getType());
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

						JavaType type = CollectionType.construct(targetClass, bindings,
								superClass, null, elemType);
						return this.objectMapper.convertValue(argument, type);
					}
					if (td.isArray()) {
						JavaType type = typeFactory.constructArrayType(
								td.getElementTypeDescriptor().getType());
						return this.objectMapper.convertValue(argument, type);
					}
				}

				throw e;
			}
		}
		return this.objectMapper.convertValue(argument, targetClass);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private Object convertListElements(TypeDescriptor td,
			@Nullable Object convertedValue) {
		if (convertedValue != null
				&& List.class.isAssignableFrom(convertedValue.getClass())
				&& td.isCollection() && td.getElementTypeDescriptor() != null) {
			Class<?> elementType = td.getElementTypeDescriptor().getType();

			Collection<Object> convertedList = new ArrayList<>();
			for (Object record : (List<Object>) convertedValue) {
				Object convertedObject = this.objectMapper.convertValue(record,
						elementType);
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
	@Nullable
	private Object doInvoke(Object... args) throws Exception {
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
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				String text = getInvocationErrorMessage("Failed to invoke handler method",
						args);
				throw new IllegalStateException(text, targetException);
			}
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
	protected void assertTargetBean(Method method, Object targetBean, Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String text = "The mapped handler method class '"
					+ methodDeclaringClass.getName()
					+ "' is not an instance of the actual endpoint bean class '"
					+ targetBeanClass.getName() + "'. If the endpoint requires proxying "
					+ "(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(text, args));
		}
	}

	private String getInvocationErrorMessage(String text, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(text));
		sb.append("Resolved arguments: \n");
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName())
						.append("] ");
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

}
