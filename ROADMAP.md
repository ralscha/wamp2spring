# WAMP Implementation Tasks

## Priority 1

- [x] Complete multi-realm routing and realm-aware registries.
- [ ] Finish URI validation and reserved URI enforcement for all URI-bearing fields.
- [ ] Close remaining call canceling edge cases.
- [ ] Close remaining call timeout edge cases.
- [ ] Complete the WAMP-level authorization framework beyond the Spring security integration.

## Priority 2

- [ ] Implement WAMP-CRA authentication.
- [ ] Implement dynamic authentication procedures.
- [ ] Implement call trustlevel propagation in `INVOCATION.Details`.
- [ ] Implement publication trustlevel propagation in `EVENT.Details`.
- [ ] Implement event history retrieval with `wamp.subscription.get_events`.
- [ ] Implement WAMP JSON binary payload conventions.

## Priority 3

- [ ] Implement progressive call invocations.
- [ ] Implement sharded registration routing with `rkey`.
- [ ] Implement sharded subscription routing with `nkey` and `rkey`.
- [ ] Implement procedure reflection APIs under `wamp.reflection.*`.
- [ ] Implement topic reflection APIs under `wamp.reflection.*`.
- [ ] Implement call rerouting for `wamp.error.unavailable`.

## Priority 4

- [ ] Implement WAMP-SCRAM authentication.
- [ ] Implement Cryptosign authentication.
- [ ] Implement RawSocket transport.
- [ ] Implement batched WebSocket subprotocol support.
- [ ] Implement HTTP longpoll fallback transport.
- [ ] Implement Payload Passthru Mode.
- [ ] Implement payload end-to-end encryption support.

## Priority 5

- [ ] Implement WAMP IDL and interface catalogs.
- [ ] Implement interface reflection procedures and events.
- [ ] Implement router-to-router links.


WAMP specification: https://wamp-proto.org/wamp_latest_ietf.txt

