/**
 * Copyright the original author or authors.
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
package ch.rasc.wamp2spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.config.PrincipalMethodArgumentResolver;
import ch.rasc.wamp2spring.config.WampMessageMethodArgumentResolver;

@SuppressWarnings("unused")
public class InvocableHandlerMethodTest {

	private InvocableHandlerMethod invocableHandlerMethod;

	@BeforeEach
	public void setup() throws Exception {
		this.invocableHandlerMethod = new InvocableHandlerMethod(
				new HandlerMethod(this, "setup"));

		HandlerMethodArgumentResolverComposite argumentResolvers = new HandlerMethodArgumentResolverComposite();
		argumentResolvers.addResolver(new WampMessageMethodArgumentResolver());
		argumentResolvers.addResolver(new PrincipalMethodArgumentResolver());
		this.invocableHandlerMethod.setArgumentResolvers(argumentResolvers);
		this.invocableHandlerMethod
				.setConversionService(new DefaultFormattingConversionService());
		this.invocableHandlerMethod.setObjectMapper(new ObjectMapper());
		this.invocableHandlerMethod
				.setParameterNameDiscoverer(new DefaultParameterNameDiscoverer());
	}

	@Test
	public void testToString() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("stringParam", String.class);
		MethodParameter param = new MethodParameter(testMethod, 0);
		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();
		assertThat(this.invocableHandlerMethod.convert(param, "str")).isEqualTo("str");
		assertThat(this.invocableHandlerMethod.convert(param, (byte) 1)).isEqualTo("1");
		assertThat(this.invocableHandlerMethod.convert(param, (short) 2)).isEqualTo("2");
		assertThat(this.invocableHandlerMethod.convert(param, 3)).isEqualTo("3");
		assertThat(this.invocableHandlerMethod.convert(param, 4L)).isEqualTo("4");
		assertThat(this.invocableHandlerMethod.convert(param, 5.5f)).isEqualTo("5.5");
		assertThat(this.invocableHandlerMethod.convert(param, 6.6)).isEqualTo("6.6");
		assertThat(this.invocableHandlerMethod.convert(param, new BigDecimal("3.141")))
				.isEqualTo("3.141");
	}

	@Test
	public void testToint() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("intParam", Integer.TYPE);
		MethodParameter param = new MethodParameter(testMethod, 0);
		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();
		assertThat(this.invocableHandlerMethod.convert(param, (byte) 1)).isEqualTo(1);
		assertThat(this.invocableHandlerMethod.convert(param, (short) 2)).isEqualTo(2);
		assertThat(this.invocableHandlerMethod.convert(param, 3)).isEqualTo(3);
		assertThat(this.invocableHandlerMethod.convert(param, 4L)).isEqualTo(4);
		assertThat(this.invocableHandlerMethod.convert(param, 5.5f)).isEqualTo(5);
		assertThat(this.invocableHandlerMethod.convert(param, 6.6)).isEqualTo(6);
		assertThat(this.invocableHandlerMethod.convert(param, new BigDecimal("3.141")))
				.isEqualTo(3);
	}

	@Test
	public void testTointException() throws NoSuchMethodException, SecurityException {
		Assertions.assertThrows(ConversionFailedException.class, () -> {
			Method testMethod = getClass().getDeclaredMethod("intParam", Integer.TYPE);
			MethodParameter param = new MethodParameter(testMethod, 0);
			assertThat(this.invocableHandlerMethod.convert(param, "str"))
					.isEqualTo("str");
		});
	}

	@Test
	public void testToInteger() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("IntegerParam", Integer.class);
		MethodParameter param = new MethodParameter(testMethod, 0);
		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();
		assertThat(this.invocableHandlerMethod.convert(param, (byte) 1))
				.isEqualTo(Integer.valueOf(1));
		assertThat(this.invocableHandlerMethod.convert(param, (short) 2))
				.isEqualTo(Integer.valueOf(2));
		assertThat(this.invocableHandlerMethod.convert(param, 3))
				.isEqualTo(Integer.valueOf(3));
		assertThat(this.invocableHandlerMethod.convert(param, 4L))
				.isEqualTo(Integer.valueOf(4));
		assertThat(this.invocableHandlerMethod.convert(param, 5.5f))
				.isEqualTo(Integer.valueOf(5));
		assertThat(this.invocableHandlerMethod.convert(param, 6.6))
				.isEqualTo(Integer.valueOf(6));
		assertThat(this.invocableHandlerMethod.convert(param, new BigDecimal("3.141")))
				.isEqualTo(Integer.valueOf(3));
	}

	@Test
	public void testToIntegerException() {
		Assertions.assertThrows(ConversionFailedException.class, () -> {
			Method testMethod = getClass().getDeclaredMethod("IntegerParam",
					Integer.class);
			MethodParameter param = new MethodParameter(testMethod, 0);
			assertThat(this.invocableHandlerMethod.convert(param, "str"))
					.isEqualTo("str");
		});
	}

	@Test
	public void testToBigDecimal() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("BigDecimalParam",
				BigDecimal.class);
		MethodParameter param = new MethodParameter(testMethod, 0);
		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();
		assertThat(this.invocableHandlerMethod.convert(param, (byte) 1))
				.isEqualTo(new BigDecimal("1"));
		assertThat(this.invocableHandlerMethod.convert(param, (short) 2))
				.isEqualTo(new BigDecimal("2"));
		assertThat(this.invocableHandlerMethod.convert(param, 3))
				.isEqualTo(new BigDecimal("3"));
		assertThat(this.invocableHandlerMethod.convert(param, 4L))
				.isEqualTo(new BigDecimal("4"));
		assertThat(this.invocableHandlerMethod.convert(param, 5.5f))
				.isEqualTo(new BigDecimal("5.5"));
		assertThat(this.invocableHandlerMethod.convert(param, 6.6))
				.isEqualTo(new BigDecimal("6.6"));
		assertThat(this.invocableHandlerMethod.convert(param, new BigDecimal("3.141")))
				.isEqualTo(new BigDecimal("3.141"));
	}

	@Test
	public void testToBigDecimalException()
			throws NoSuchMethodException, SecurityException {
		Assertions.assertThrows(ConversionFailedException.class, () -> {
			Method testMethod = getClass().getDeclaredMethod("BigDecimalParam",
					BigDecimal.class);
			MethodParameter param = new MethodParameter(testMethod, 0);
			assertThat(this.invocableHandlerMethod.convert(param, "str"))
					.isEqualTo("str");
		});
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testToOptional() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("OptionalParam", Optional.class);
		MethodParameter param = new MethodParameter(testMethod, 0);

		Object value = this.invocableHandlerMethod.convert(param, null);
		assertThat(value).isEqualTo(Optional.empty());

		value = this.invocableHandlerMethod.convert(param, "str");
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo("str");

		value = this.invocableHandlerMethod.convert(param, (byte) 1);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo((byte) 1);

		value = this.invocableHandlerMethod.convert(param, (short) 2);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo((short) 2);

		value = this.invocableHandlerMethod.convert(param, 3);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo(3);

		value = this.invocableHandlerMethod.convert(param, 4L);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo(4L);

		value = this.invocableHandlerMethod.convert(param, 5.5f);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo(5.5f);

		value = this.invocableHandlerMethod.convert(param, 6.6);
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo(6.6);

		value = this.invocableHandlerMethod.convert(param, new BigDecimal("3.141"));
		assertThat(value).isInstanceOf(Optional.class);
		assertThat(((Optional) value).get()).isEqualTo(new BigDecimal("3.141"));
	}

	@Test
	public void testDto() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("dtoParam", TestDto.class);
		MethodParameter param = new MethodParameter(testMethod, 0);

		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();

		TestDto dto = new TestDto();
		dto.setV1("str");
		dto.setV2(1);
		dto.setV3(Integer.valueOf(2));
		dto.setV4(new BigDecimal("3.1"));

		assertThat(this.invocableHandlerMethod.convert(param, dto)).isEqualTo(dto);

		Map<String, Object> fromJson = new HashMap<>();
		fromJson.put("v1", "str");
		fromJson.put("v2", 1);
		fromJson.put("v3", 2);
		fromJson.put("v4", "3.1");

		assertThat(this.invocableHandlerMethod.convert(param, fromJson)).isEqualTo(dto);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testToList() throws NoSuchMethodException, SecurityException {
		Method testMethod = getClass().getDeclaredMethod("listParam", List.class);
		MethodParameter param = new MethodParameter(testMethod, 0);
		assertThat(this.invocableHandlerMethod.convert(param, null)).isNull();
		assertThat((List) this.invocableHandlerMethod.convert(param, "1")).hasSize(1)
				.containsExactly("1");
		assertThat(
				(List) this.invocableHandlerMethod.convert(param, Arrays.asList(1, 2, 3)))
						.hasSize(3).containsExactly("1", "2", "3");
	}

	static class TestDto {
		private String v1;
		private int v2;
		private Integer v3;
		private BigDecimal v4;

		public String getV1() {
			return this.v1;
		}

		public void setV1(String v1) {
			this.v1 = v1;
		}

		public int getV2() {
			return this.v2;
		}

		public void setV2(int v2) {
			this.v2 = v2;
		}

		public Integer getV3() {
			return this.v3;
		}

		public void setV3(Integer v3) {
			this.v3 = v3;
		}

		public BigDecimal getV4() {
			return this.v4;
		}

		public void setV4(BigDecimal v4) {
			this.v4 = v4;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.v1, this.v2, this.v3, this.v4);
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if ((obj == null) || (getClass() != obj.getClass())) {
				return false;
			}
			TestDto other = (TestDto) obj;
			if (!Objects.equals(this.v1, other.v1)) {
				return false;
			}
			if (this.v2 != other.v2) {
				return false;
			}
			if (!Objects.equals(this.v3, other.v3)) {
				return false;
			}
			if (!Objects.equals(this.v4, other.v4)) {
				return false;
			}
			return true;
		}

	}

}
