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

import java.time.Duration;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.messaging.SubscribableChannel;

import ch.rasc.wamp2spring.config.WampConfiguration;
import ch.rasc.wamp2spring.config.WampConfigurer;
import ch.rasc.wamp2spring.servlet.WampSubProtocolHandler;
import ch.rasc.wamp2spring.servlet.WampSessionSupport;
import ch.rasc.wamp2spring.auth.WampAuthenticationProvider;

import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class WampServletLongpollConfiguration extends WampConfiguration implements ImportAware {

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		super.setImportMetadata(importMetadata, EnableServletWampLongpoll.class.getName());
	}

	@Bean
	public WampSessionSupport wampSessionSupport(ObjectProvider<WampAuthenticationProvider> authenticationProviders,
			org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
		return new WampSessionSupport(this.features, authenticationProviders.orderedStream().toList(),
				applicationEventPublisher);
	}

	@Bean
	public LongpollTransportRegistry longpollTransportRegistry(SubscribableChannel clientOutboundChannel,
			WampSessionSupport sessionSupport) {
		return new LongpollTransportRegistry(
				Map.of(WampSubProtocolHandler.JSON_PROTOCOL, jsonJsonFactory(), WampSubProtocolHandler.MSGPACK_PROTOCOL,
						msgpackJsonFactory(), WampSubProtocolHandler.CBOR_PROTOCOL, cborJsonFactory(),
						WampSubProtocolHandler.SMILE_PROTOCOL, smileJsonFactory()),
				resolveMaxQueueSize(), resolveReceiveTimeout(), resolveTransportIdleTimeout(),
				transport -> sessionSupport.afterSessionEnded(transport.getTransportId(), transport.getPrincipal(),
						transport.getAttributes()));
	}

	@Bean
	public WampLongpollController wampLongpollController(LongpollTransportRegistry transportRegistry,
			WampSessionSupport sessionSupport) {
		clientOutboundChannel().subscribe(transportRegistry);
		return new WampLongpollController(transportRegistry, clientInboundChannel(), sessionSupport);
	}

	protected Duration getReceiveTimeout() {
		return Duration.ofSeconds(30);
	}

	protected int getMaxQueueSize() {
		return 100;
	}

	protected Duration getTransportIdleTimeout() {
		return Duration.ofSeconds(60);
	}

	private Duration resolveReceiveTimeout() {
		Duration receiveTimeout = getReceiveTimeout();
		for (WampConfigurer configurer : this.configurers) {
			if (configurer instanceof WampServletLongpollConfigurer longpollConfigurer) {
				receiveTimeout = longpollConfigurer.getReceiveTimeout();
			}
		}
		return receiveTimeout;
	}

	private int resolveMaxQueueSize() {
		int maxQueueSize = getMaxQueueSize();
		for (WampConfigurer configurer : this.configurers) {
			if (configurer instanceof WampServletLongpollConfigurer longpollConfigurer) {
				maxQueueSize = longpollConfigurer.getMaxQueueSize();
			}
		}
		return maxQueueSize;
	}

	private Duration resolveTransportIdleTimeout() {
		Duration transportIdleTimeout = getTransportIdleTimeout();
		for (WampConfigurer configurer : this.configurers) {
			if (configurer instanceof WampServletLongpollConfigurer longpollConfigurer) {
				transportIdleTimeout = longpollConfigurer.getTransportIdleTimeout();
			}
		}
		return transportIdleTimeout;
	}

}
