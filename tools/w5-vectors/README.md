# W5 golden vectors

`vectors.mjs` builds the W5 (v5r1) addresses, state init and unsigned transfer bodies with ton-org's
reference libraries (`@ton/ton`, `@ton/core`, `@ton/crypto`, versions pinned in `package.json`) from a
throwaway test key, and prints them as `W5TestVectors.kt`. `W5WalletTest` and `TonConnectKitConnectTest`
compare the kit's output against them.

Regenerate (Node 20+):

```sh
cd tools/w5-vectors
npm ci
node vectors.mjs > ../../tonkit/src/test/java/io/horizontalsystems/tonkit/core/W5TestVectors.kt
```

The inputs in `vectors.mjs` mirror the tests; change both together. A vector mismatch means the kit is
wrong until proven otherwise; never edit a vector to make a test pass.

One deliberate convention: on deploy (seqno 0) the kit writes 32 one-bits instead of `valid_until`, as for
v4R2, so the deploy vector passes `timeout: 0xffffffff`.
