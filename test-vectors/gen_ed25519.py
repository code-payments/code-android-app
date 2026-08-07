#!/usr/bin/env python3
"""Generate authoritative ed25519 test vectors (RFC 8032 standard, via `cryptography`).

Each vector: a 32-byte seed + a message -> the standard public key and signature.
Both apps' ed25519 must reproduce these; any mismatch is a real divergence.
Includes the RFC 8032 s7.1 vectors (as a correctness anchor) plus a few app-shaped cases.
"""
import json
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

# (name, seed_hex, message_hex).  RFC 8032 uses the 32-byte secret as the seed.
CASES = [
    # RFC 8032 Section 7.1 anchors (authoritative).
    ("rfc8032-test1", "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60", ""),
    ("rfc8032-test2", "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6f8", "72"),
    ("rfc8032-test3", "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7", "af82"),
    # App-shaped: fixed seeds, Solana-ish 32-byte and larger messages (deterministic, no RNG).
    ("zero-seed-empty", "0000000000000000000000000000000000000000000000000000000000000000", ""),
    ("zero-seed-32b",   "0000000000000000000000000000000000000000000000000000000000000000",
                        "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
    ("seed-ff-tx",      "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                        "0100000000000000" + "de" * 64),  # 8-byte header + 64-byte pseudo-message
]

out = []
for name, seed_hex, msg_hex in CASES:
    seed = bytes.fromhex(seed_hex)
    msg = bytes.fromhex(msg_hex)
    sk = Ed25519PrivateKey.from_private_bytes(seed)
    pub = sk.public_key().public_bytes_raw()
    sig = sk.sign(msg)
    out.append({
        "name": name,
        "seed": seed_hex,
        "message": msg_hex,
        "publicKey": pub.hex(),
        "signature": sig.hex(),
    })

doc = {
    "algorithm": "ed25519",
    "spec": "RFC 8032 (SHA-512). seed=32-byte private seed; publicKey/signature are standard outputs.",
    "note": "Cross-platform parity gate: both apps must reproduce publicKey and signature for each seed/message.",
    "vectors": out,
}
print(json.dumps(doc, indent=2))
