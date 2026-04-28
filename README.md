![Build Status](https://github.com/ralscha/wamp2spring/workflows/test/badge.svg)

*wamp2spring* is a Java implementation of the [WAMP specification](http://wamp-proto.org/spec/) built on top of the WebSocket support of Spring 7.   
WAMP is a WebSocket subprotocol that provides two application messaging patterns: Remote Procedure Calls and Publish / Subscribe. 

## Implementation
*wamp2spring* implements the Basic Profile with a single shared routing scope. Connections, registrations,
subscriptions, and meta APIs are handled without realm-based partitioning during session establishment or routing.

Additionally *wamp2spring* implements a few features from the Advanced Profile:

|Feature                      |Remark                                                                                                                                                    |
|:----------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------|
|call_canceling               |Dealer-side `CANCEL` / `INTERRUPT` with `skip`, `kill`, and `killnowait`. Advanced cancel modes require caller negotiation; unsupported callees fall back to dealer-managed behavior.|
|call_timeout                 |Dealer-enforced `CALL.Options.timeout`. Timeout intent is forwarded to callees that advertise support, but timeout semantics remain enforced by the dealer.|
|progressive_call_results     |Dealer-side negotiation and forwarding of `CALL.Options.receive_progress`, `INVOCATION.Details.receive_progress`, progressive `YIELD`, and progressive `RESULT`, while keeping the invocation open until the final `YIELD` or `ERROR`.|
|caller_identification        |disclose_me option in the CALL message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.3.5)                                               |
|registration_revocation      |Dealer-side administrative revocation of registrations, with extended `UNREGISTERED` delivery to callees that advertised support.|
|subscriber_blackwhite_listing|Exclude and include receivers with their WAMP session id. *Only eligible and exclude options are implemented.* [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.1).|
|publisher_exclusion          |exclude_me option in the PUBLISH message. By default the publisher is excluded from receiving the EVENT message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.2)                                               |
|publisher_identification     |disclose_me option in the PUBLISH message. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.3)|
|pattern_based_subscription   |Prefix- and wildcard matching policies for subscriptions. [Specification](http://wamp-proto.org/static/rfc/draft-oberstet-hybi-crossbar-wamp.html#rfc.section.14.4.6)                                               |
|event_retention              |[Specification](https://github.com/wamp-proto/wamp-proto/blob/da34d9bd833beeb6f9cc8bc89faf8138d710aa78/rfc/text/advanced/ap_pubsub_event_retention.md)|
|testament_meta_api           |`wamp.session.add_testament` and `wamp.session.flush_testaments` with detached/destroyed scopes and disconnect-triggered publication order.| 

For Advanced RPC timeout handling, the dealer is the source of truth once a caller and the router negotiated `call_timeout`.
If a callee also advertises timeout support, the dealer forwards the timeout value in `INVOCATION.Details.timeout`.
If the callee does not advertise timeout support, the dealer still enforces the timeout and returns the appropriate timeout error to the caller.

WAMP-level authorization can be extended with one or more Spring beans implementing `WampAuthorizer`.
The dealer and broker consult these authorizers for `REGISTER`, `CALL`, `SUBSCRIBE`, and `PUBLISH` before routing,
and authorizers can deny an operation with `wamp.error.not_authorized` or `wamp.error.authorization_failed`.

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
See [Wiki](https://github.com/ralscha/wamp2spring/wiki/Quickstart)

## Maven
See [Wiki](https://github.com/ralscha/wamp2spring/wiki/Maven)

## Example applications
You find a collection of example applications in the [wamp2spring-demo](https://github.com/ralscha/wamp2spring-demo) GitHub repository.


## Changelog

See [Wiki](https://github.com/ralscha/wamp2spring/wiki/Changelog)


## More information
See [Wiki](https://github.com/ralscha/wamp2spring/wiki/Links)

  
## License
Code released under [the Apache license](http://www.apache.org/licenses/).
