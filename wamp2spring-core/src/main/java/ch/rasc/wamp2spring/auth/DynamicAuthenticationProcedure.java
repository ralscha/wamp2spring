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
package ch.rasc.wamp2spring.auth;

import org.jspecify.annotations.Nullable;

import ch.rasc.wamp2spring.message.AuthenticateMessage;
import ch.rasc.wamp2spring.message.HelloMessage;

/**
 * Application supplied procedure that drives a custom WAMP authentication method.
 */
public interface DynamicAuthenticationProcedure {

	WampAuthenticationChallenge challenge(HelloMessage helloMessage);

	@Nullable WampAuthentication authenticate(HelloMessage helloMessage, AuthenticateMessage authenticateMessage,
			@Nullable Object challengeState);

}