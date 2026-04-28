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
package ch.rasc.wamp2spring.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import ch.rasc.wamp2spring.pubsub.MatchPolicy;
import ch.rasc.wamp2spring.rpc.InvocationPolicy;

public class RegisterMessageTest extends BaseMessageTest {

	@Test
	public void serializeTest() {
		RegisterMessage registerMessage = new RegisterMessage(11, "bean.method");
		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(11);
		assertThat(registerMessage.getProcedure()).isEqualTo("bean.method");
		assertThat(registerMessage.isDiscloseCaller()).isFalse();

		String json = serializeToJson(registerMessage);
		assertThat(json).isEqualTo("[64,11,{},\"bean.method\"]");

		registerMessage = new RegisterMessage(927639114088448L, "com.maypp.add2", true);
		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(927639114088448L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.maypp.add2");
		assertThat(registerMessage.isDiscloseCaller()).isTrue();
		assertThat(registerMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);

		json = serializeToJson(registerMessage);
		assertThat(json).isEqualTo("[64,927639114088448,{\"disclose_caller\":true},\"com.maypp.add2\"]");

		registerMessage = new RegisterMessage(1L, "com.myapp.prefix", true, MatchPolicy.PREFIX);
		assertThat(registerMessage.getMatchPolicy()).isEqualTo(MatchPolicy.PREFIX);
		json = serializeToJson(registerMessage);
		assertThat(json).isEqualTo("[64,1,{\"disclose_caller\":true,\"match\":\"prefix\"},\"com.myapp.prefix\"]");

		registerMessage = new RegisterMessage(2L, "com.myapp.worker", false, MatchPolicy.EXACT,
				InvocationPolicy.ROUNDROBIN);
		assertThat(registerMessage.getInvokePolicy()).isEqualTo(InvocationPolicy.ROUNDROBIN);
		json = serializeToJson(registerMessage);
		assertThat(json).isEqualTo("[64,2,{\"invoke\":\"roundrobin\"},\"com.myapp.worker\"]");
	}

	@Test
	public void deserializeTest() throws IOException {
		String json = "[64, 25349185, {}, \"com.myapp.myprocedure\"]";
		RegisterMessage registerMessage = WampMessage.deserialize(getJsonFactory(),
				json.getBytes(StandardCharsets.UTF_8));

		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(25349185L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.myapp.myprocedure");
		assertThat(registerMessage.isDiscloseCaller()).isFalse();
		assertThat(registerMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);

		json = "[64, 927639114088448, {\"disclose_caller\":true},\"com.maypp.add2\"]";
		registerMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));

		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(927639114088448L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.maypp.add2");
		assertThat(registerMessage.isDiscloseCaller()).isTrue();
		assertThat(registerMessage.getMatchPolicy()).isEqualTo(MatchPolicy.EXACT);

		json = "[64, 2, {\"match\":\"wildcard\"},\"com.myapp..create\"]";
		registerMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(2L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.myapp..create");
		assertThat(registerMessage.getMatchPolicy()).isEqualTo(MatchPolicy.WILDCARD);

		json = "[64, 3, {\"invoke\":\"last\"},\"com.myapp.worker\"]";
		registerMessage = WampMessage.deserialize(getJsonFactory(), json.getBytes(StandardCharsets.UTF_8));
		assertThat(registerMessage.getCode()).isEqualTo(64);
		assertThat(registerMessage.getRequestId()).isEqualTo(3L);
		assertThat(registerMessage.getProcedure()).isEqualTo("com.myapp.worker");
		assertThat(registerMessage.getInvokePolicy()).isEqualTo(InvocationPolicy.LAST);

	}

}
