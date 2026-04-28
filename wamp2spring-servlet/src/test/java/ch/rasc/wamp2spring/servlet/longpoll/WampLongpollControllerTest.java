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
package ch.rasc.wamp2spring.servlet.longpoll;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

import ch.rasc.wamp2spring.WampPublisher;
import ch.rasc.wamp2spring.WampError;
import ch.rasc.wamp2spring.message.AbortMessage;
import ch.rasc.wamp2spring.message.GoodbyeMessage;
import ch.rasc.wamp2spring.message.HelloMessage;
import ch.rasc.wamp2spring.message.EventMessage;
import ch.rasc.wamp2spring.message.PublishMessage;
import ch.rasc.wamp2spring.message.PublishedMessage;
import ch.rasc.wamp2spring.message.SubscribeMessage;
import ch.rasc.wamp2spring.message.SubscribedMessage;
import ch.rasc.wamp2spring.message.WampMessage;
import ch.rasc.wamp2spring.message.WampRole;
import ch.rasc.wamp2spring.message.WelcomeMessage;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;
import ch.rasc.wamp2spring.util.WampJson;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = WampLongpollControllerTest.Config.class)
public class WampLongpollControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private LongpollTransportRegistry transportRegistry;

	@Autowired
	private WampPublisher wampPublisher;

	private RestClient restClient;

	private final ObjectMapper objectMapper = WampJson.createJsonObjectMapper();

	@BeforeEach
	public void setup() {
		this.restClient = RestClient.builder().baseUrl("http://localhost:" + this.port).build();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void openCreatesTransportAndCloseRemovesIt() {
		Map<String, String> response = (Map<String, String>) this.restClient.post()
			.uri("/wamp/open")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("protocol", WampSubProtocolHandler.JSON_PROTOCOL))
			.retrieve()
			.body(Map.class);

		assertThat(response).containsEntry("protocol", WampSubProtocolHandler.JSON_PROTOCOL);
		String transportId = response.get("transport");
		assertThat(transportId).isNotBlank();
		assertThat(this.transportRegistry.get(transportId)).isNotNull();

		int closeStatus = this.restClient.post()
			.uri("/wamp/{transportId}/close", transportId)
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(closeStatus).isEqualTo(204);
		assertThat(this.transportRegistry.get(transportId)).isNull();
	}

	@Test
	public void openRejectsUnsupportedProtocol() {
		int status = this.restClient.post()
			.uri("/wamp/open")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("protocol", "wamp.2.invalid"))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(status).isEqualTo(400);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void receiveTimesOutWithNoContentWhenQueueIsEmpty() {
		Map<String, String> response = (Map<String, String>) this.restClient.post()
			.uri("/wamp/open")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("protocol", WampSubProtocolHandler.JSON_PROTOCOL))
			.retrieve()
			.body(Map.class);

		int status = this.restClient.post()
			.uri("/wamp/{transportId}/receive", response.get("transport"))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(status).isEqualTo(204);
	}

	@Test
	public void helloReturnsWelcomeOnReceive() throws IOException {
		String transportId = openJsonTransport();
		HelloMessage helloMessage = new HelloMessage(
				List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")));

		int sendStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(helloMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(sendStatus).isEqualTo(204);

		ResponseEntity<byte[]> receiveResponse = receive(transportId);
		assertThat(receiveResponse.getStatusCode().value()).isEqualTo(200);
		assertThat(receiveResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
			.startsWith(MediaType.APPLICATION_JSON_VALUE);

		WelcomeMessage welcomeMessage = (WelcomeMessage) deserialize(receiveResponse.getBody());
		assertThat(welcomeMessage.getSessionId()).isPositive();
		assertThat(welcomeMessage.getAuthMethod()).isEqualTo("anonymous");
		assertThat(this.transportRegistry.get(transportId)).isNotNull();
		assertThat(this.transportRegistry.get(transportId).getAttributes()).containsKey("WAMP_SESSION_ID");
	}

	@Test
	public void sendBeforeSessionEstablishedReturnsAbortAndClosesTransport() throws IOException {
		String transportId = openJsonTransport();
		PublishMessage publishMessage = PublishMessage.builder(1L, "topic.test").build();

		int sendStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(publishMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(sendStatus).isEqualTo(204);

		ResponseEntity<byte[]> receiveResponse = receive(transportId);
		AbortMessage abortMessage = (AbortMessage) deserialize(receiveResponse.getBody());
		assertThat(abortMessage.getReason()).isEqualTo(WampError.PROTOCOL_VIOLATION.getExternalValue());
		assertThat(this.transportRegistry.get(transportId)).isNull();
	}

	@Test
	public void goodbyeIsReturnedAndTransportRemovedAfterDrain() throws IOException {
		String transportId = openJsonTransport();
		HelloMessage helloMessage = new HelloMessage(
				List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")));

		this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(helloMessage))
			.retrieve()
			.toBodilessEntity();
		receive(transportId);

		int sendStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(new GoodbyeMessage(WampError.GOODBYE_AND_OUT)))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(sendStatus).isEqualTo(204);

		ResponseEntity<byte[]> receiveResponse = receive(transportId);
		GoodbyeMessage goodbyeMessage = (GoodbyeMessage) deserialize(receiveResponse.getBody());
		assertThat(goodbyeMessage.getReason()).isEqualTo(WampError.GOODBYE_AND_OUT.getExternalValue());
		assertThat(this.transportRegistry.get(transportId)).isNull();
	}

	@Test
	public void subscribeAndServerPublishRoundTripsOverLongpoll() throws IOException {
		String transportId = openJsonTransport();
		helloAndReceiveWelcome(transportId);

		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "topic.longpoll");
		int subscribeStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(subscribeMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(subscribeStatus).isEqualTo(204);

		ResponseEntity<byte[]> subscribedResponse = receive(transportId);
		SubscribedMessage subscribedMessage = (SubscribedMessage) deserialize(subscribedResponse.getBody());
		assertThat(subscribedMessage.getRequestId()).isEqualTo(1L);
		assertThat(subscribedMessage.getSubscriptionId()).isPositive();

		this.wampPublisher.publishToAll("topic.longpoll", "payload");

		ResponseEntity<byte[]> eventResponse = receive(transportId);
		EventMessage eventMessage = (EventMessage) deserialize(eventResponse.getBody());
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscribedMessage.getSubscriptionId());
		assertThat(eventMessage.getPublicationId()).isPositive();
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getArguments()).containsExactly("payload");
		assertThat(eventMessage.getArgumentsKw()).isNull();
	}

	@Test
	public void clientToClientPublishRoundTripsOverLongpoll() throws IOException {
		String subscriberTransportId = openJsonTransport();
		String publisherTransportId = openJsonTransport();
		helloAndReceiveWelcome(subscriberTransportId);
		helloAndReceiveWelcome(publisherTransportId);

		SubscribeMessage subscribeMessage = new SubscribeMessage(1L, "topic.client.longpoll");
		int subscribeStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", subscriberTransportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(subscribeMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(subscribeStatus).isEqualTo(204);

		ResponseEntity<byte[]> subscribedResponse = receive(subscriberTransportId);
		SubscribedMessage subscribedMessage = (SubscribedMessage) deserialize(subscribedResponse.getBody());
		assertThat(subscribedMessage.getRequestId()).isEqualTo(1L);
		assertThat(subscribedMessage.getSubscriptionId()).isPositive();

		PublishMessage publishMessage = PublishMessage.builder(2L, "topic.client.longpoll")
			.addArgument("from client")
			.acknowledge()
			.build();
		int publishStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", publisherTransportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(publishMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());

		assertThat(publishStatus).isEqualTo(204);

		ResponseEntity<byte[]> publishedResponse = receive(publisherTransportId);
		PublishedMessage publishedMessage = (PublishedMessage) deserialize(publishedResponse.getBody());
		assertThat(publishedMessage.getRequestId()).isEqualTo(2L);
		assertThat(publishedMessage.getPublicationId()).isPositive();

		ResponseEntity<byte[]> eventResponse = receive(subscriberTransportId);
		EventMessage eventMessage = (EventMessage) deserialize(eventResponse.getBody());
		assertThat(eventMessage.getSubscriptionId()).isEqualTo(subscribedMessage.getSubscriptionId());
		assertThat(eventMessage.getPublicationId()).isEqualTo(publishedMessage.getPublicationId());
		assertThat(eventMessage.getTopic()).isNull();
		assertThat(eventMessage.getArguments()).containsExactly("from client");
		assertThat(eventMessage.getArgumentsKw()).isNull();
	}

	private WelcomeMessage helloAndReceiveWelcome(String transportId) throws IOException {
		HelloMessage helloMessage = new HelloMessage(
				List.of(new WampRole("publisher"), new WampRole("subscriber"), new WampRole("caller")));
		int sendStatus = this.restClient.post()
			.uri("/wamp/{transportId}/send", transportId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(serialize(helloMessage))
			.exchange((request, responseMessage) -> responseMessage.getStatusCode().value());
		assertThat(sendStatus).isEqualTo(204);
		ResponseEntity<byte[]> receiveResponse = receive(transportId);
		return (WelcomeMessage) deserialize(receiveResponse.getBody());
	}

	private String openJsonTransport() {
		@SuppressWarnings("unchecked")
		Map<String, String> response = (Map<String, String>) this.restClient.post()
			.uri("/wamp/open")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("protocol", WampSubProtocolHandler.JSON_PROTOCOL))
			.retrieve()
			.body(Map.class);
		return response.get("transport");
	}

	private ResponseEntity<byte[]> receive(String transportId) {
		return this.restClient.post()
			.uri("/wamp/{transportId}/receive", transportId)
			.exchange((request, responseMessage) -> ResponseEntity.status(responseMessage.getStatusCode())
				.headers(responseMessage.getHeaders())
				.body(responseMessage.getBody().readAllBytes()));
	}

	private byte[] serialize(WampMessage wampMessage) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				JsonGenerator generator = this.objectMapper.createGenerator(bos)) {
			generator.writeStartArray();
			wampMessage.serialize(generator);
			generator.writeEndArray();
			generator.close();
			return bos.toByteArray();
		}
	}

	private WampMessage deserialize(byte[] payload) throws IOException {
		return WampMessage.deserialize(this.objectMapper, payload);
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableServletWampLongpoll
	static class Config implements WampServletLongpollConfigurer {

		@Override
		public Duration getReceiveTimeout() {
			return Duration.ofMillis(25);
		}

	}

}