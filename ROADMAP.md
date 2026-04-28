# WAMP Implementation Tasks

## Priority 1

- [x] Finish URI validation and reserved URI enforcement for all URI-bearing fields.
- [x] Implement WAMP-level authorization.

## Priority 2

- [x] Implement WAMP-CRA authentication.
- [x] Implement dynamic authentication procedures.
- [x] Implement call trustlevel propagation in `INVOCATION.Details`.
- [x] Implement publication trustlevel propagation in `EVENT.Details`.
- [x] Implement event history retrieval with `wamp.subscription.get_events`.
- [x] Implement WAMP JSON binary payload conventions.

## Priority 3

- [ ] Implement progressive call invocations.
- [ ] Implement sharded registration routing with `rkey`.
- [ ] Implement sharded subscription routing with `nkey` and `rkey`.
- [x] Implement procedure reflection APIs under `wamp.reflection.*`.
- [x] Implement topic reflection APIs under `wamp.reflection.*`.
- [x] Implement call rerouting for `wamp.error.unavailable`.

## Priority 4

- [ ] Implement WAMP-SCRAM authentication.
- [ ] Implement Cryptosign authentication.
- [ ] Implement RawSocket transport.
- [ ] Implement batched WebSocket subprotocol support.
- [ ] Implement HTTP longpoll transport.
- [ ] Implement Payload Passthru Mode.
- [ ] Implement payload end-to-end encryption support.

## Priority 5

- [ ] Implement WAMP IDL and interface catalogs.
- [ ] Implement interface reflection procedures and events.
- [ ] Implement router-to-router links.


WAMP specification: https://wamp-proto.org/wamp_latest_ietf.txt

