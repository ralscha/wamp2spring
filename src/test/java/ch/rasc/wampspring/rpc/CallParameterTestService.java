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
package ch.rasc.wampspring.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Service;

import ch.rasc.wampspring.annotation.WampProcedure;
import ch.rasc.wampspring.message.CallMessage;

@Service
public class CallParameterTestService {

	@WampProcedure(value = "headerMethod")
	public String headerMethod(
			@Header(value = "WEBSOCKET_SESSION_ID") String webSocketSessionId) {
		return "headerMethod called: " + webSocketSessionId;
	}

	@WampProcedure(value = "headersMethod")
	public String headersMethod(@Headers Map<String, Object> headers) {

		assertThat(headers).containsOnlyKeys("WAMP_SESSION_ID", "PRINCIPAL",
				"WEBSOCKET_SESSION_ID", "WAMP_MESSAGE_CODE");

		return "headersMethod called";
	}

	@WampProcedure(name = "messageMethod")
	public String messageMethod(CallMessage message) {
		return "messageMethod called: " + message.getRequestId();
	}

	@WampProcedure(name = "mix")
	public String mix(String param1, CallMessage message, int param2,
			@Headers Map<String, Object> headers,
			@Header(value = "WAMP_MESSAGE_CODE") long code, float param3, String param4) {

		assertThat(param1).isEqualTo("param1");
		assertThat(message).isNotNull();
		assertThat(param2).isEqualTo(2);
		assertThat(headers).hasSize(4);
		assertThat(code).isEqualTo(CallMessage.CODE);
		assertThat(param3).isEqualTo(3.3f);
		assertThat(param4).isEqualTo("param4");

		return "mix";
	}

}
