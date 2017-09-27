
[![Build Status](https://api.travis-ci.org/ralscha/wamp2spring.png)](https://travis-ci.org/ralscha/wamp2spring)

*wamp2spring* is a Java implementation of the [WAMP specification](http://wamp-proto.org/spec/) built on top of the WebSocket support of Spring 5.   
WAMP is a WebSocket subprotocol that provides two application messaging patterns: Remote Procedure Calls and Publish / Subscribe. 

## Implementation
*wamp2spring* implements the Basic Profile, but it does not support multiple realms in one application. 
Every connection, registration and subscription exists in the same realm and *wamp2spring* ignores the realm 
parameter of the HELLO message.

Additionally *wamp2spring* implements a few features from the Advanced Profile:

|Feature                      |Remark                                                                                                                                                    |
|:----------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------|
|caller_identification        |disclose_me option in the CALL message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.3.5)                                               |
|subscriber_blackwhite_listing|Exclude and include receivers with their WAMP session id. *Only eligible and exclude options are implemented.* [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.1).|
|publisher_exclusion          |exclude_me option in the PUBLISH message. By default the publisher is excluded from receiving the EVENT message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.2)                                               |
|publisher_identification     |disclose_me option in the PUBLISH message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.3)|
|pattern_based_subscription   |Prefix- and wildcard matching policies for subscriptions. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.6)                                               |
|event_retention              |[Specification](https://github.com/wamp-proto/wamp-proto/blob/da34d9bd833beeb6f9cc8bc89faf8138d710aa78/rfc/text/advanced/ap_pubsub_event_retention.md)|

**Dataformats**   
*wamp2spring* supports JSON (wamp.2.json) and MessagePack (wamp.2.msgpack) required by the Basic Profile. In addition it
supports [CBOR](http://cbor.io/) (wamp.2.cbor) and [SMILE](https://en.wikipedia.org/wiki/Smile_(data_interchange_format)) (wamp.2.smile).


**Fallback**   
Currently *wamp2spring* does not support a fallback solution when peers cannot establish 
WebSocket connections. [autobahn-js](https://github.com/crossbario/autobahn-js) implements a fallback with long polling. 
You find the description about the protocol in the specification ([Section 14.5.3.3](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.5.3.3)).
So far I don't have a need for a fallback solution because WebSocket works fine especially when it's sent over TLS connections.
But when there is a need I will try to add this fallback solution. Pull requests are always welcome.


## Quickstart


## Maven
The released version of the library will be hosted on the Central Maven Repository. 
Until then a project can reference SNAPSHOT releases from the Sonatype repository

```
<dependency>
  <groupId>ch.rasc</groupId>
  <artifactId>wamp2spring</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
		
<repositories>
  ...
  <repository>
    <id>sonatype</id>
    <name>sonatype</name>
    <url>https://oss.sonatype.org/content/groups/public</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
    <releases>
      <enabled>true</enabled>
    </releases>
  </repository>
</repositories>		
```

## Example applications
You find a collection of example applications in the [wamp2spring-demo](https://github.com/ralscha/wamp2spring-demo) GitHub repository.


## Changelog

### 1.0.0 - September 28, 2017 (Spring 5 release date)
  * Initial release


## More links
  * [WAMP Homepage](http://wamp-proto.org/)
  * Other Java WAMP implementations
     * [jawampa](https://github.com/Matthias247/jawampa)
     * [autobahn-java](https://github.com/crossbario/autobahn-java)     
  * [WAMP libraries and routers](http://wamp-proto.org/implementations/)
  * [Crossbar.io the company behind the WAMP specification](http://crossbario.com/)

  
## License
Code released under [the Apache license](http://www.apache.org/licenses/).
