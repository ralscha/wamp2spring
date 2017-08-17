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
package ch.rasc.wamp2spring.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import ch.rasc.wamp2spring.message.RegisterMessage;
import ch.rasc.wamp2spring.message.WampMessage;

public class RegisterMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		RegisterMessage registerMessage = new RegisterMessage(11, "bean.method");

		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(11);
		assertThat(registerMessage.getProcedure()).isEqualTo("bean.method");

		String json = serializeToJson(registerMessage);
		assertThat(json).isEqualTo("[64,11,{},\"bean.method\"]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[64, 25349185, {}, \"com.myapp.myprocedure\"]";

		RegisterMessage registerMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(25349185L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.myapp.myprocedure");
	}

}
