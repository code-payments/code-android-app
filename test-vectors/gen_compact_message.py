#!/usr/bin/env python3
"""Authoritative intent-signing "compact message" vectors (SubmitIntent per-action signing).

Both apps build the SAME pre-hash bytes for a transfer/withdraw action (verified from ActionTransfer/
ActionType on both sides — identical field order, "transfer" domain, LITTLE-ENDIAN amount), then
sign SHA256(message) with the owner ed25519 key:

  transfer:  "transfer"           || source(32) || destination(32) || amount(LE8) || nonce(32) || nonceValue(32)
  withdraw:  "withdraw_and_close" || source(32) || destination(32) ||                nonce(32) || nonceValue(32)
  signature = ed25519_sign( SHA256(message), owner )

The apps' compact-message code is internal, so the per-app tests rebuild the message from public
primitives (pubkey bytes + amount encoder) and assert the bytes + SHA256 match — the layout ORDER is
verified by source inspection on both platforms; this gates the byte-level composition (pubkey
serialization, LE amount) and SHA256. The signature line is ed25519 over the hash — already gated by
ed25519.json — and is included here for completeness / the KMP reference.
"""
import json, hashlib
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

def pk(seed: int) -> bytes:
    return bytes([seed]) * 32

SIGNER_SEED = bytes([0x01]) * 32
_owner = Ed25519PrivateKey.from_private_bytes(SIGNER_SEED)

def build(domain: str, source: int, dest: int, amount, nonce: int, nonce_value: int) -> bytes:
    msg = domain.encode("utf-8") + pk(source) + pk(dest)
    if amount is not None:
        msg += int(amount).to_bytes(8, "little")   # LE8, matching both apps
    msg += pk(nonce) + pk(nonce_value)
    return msg

CASES = [
    ("transfer basic",   "transfer",           0x11, 0x22, 123456789, 0x33, 0x44),
    ("transfer zero",    "transfer",           0x11, 0x22, 0,         0x33, 0x44),
    ("transfer max u64", "transfer",           0xAA, 0xBB, (1 << 64) - 1, 0xCC, 0xDD),
    ("withdraw",         "withdraw_and_close", 0x11, 0x22, None,      0x33, 0x44),
]

vectors = []
for name, domain, s, d, amt, n, nv in CASES:
    msg = build(domain, s, d, amt, n, nv)
    digest = hashlib.sha256(msg).digest()
    sig = _owner.sign(digest)
    vectors.append({
        "name": name,
        "domain": domain,
        "sourceSeed": s, "destinationSeed": d,
        "amount": None if amt is None else str(amt),
        "nonceSeed": n, "nonceValueSeed": nv,
        "signerSeed": SIGNER_SEED.hex(),
        "message": msg.hex(),
        "sha256": digest.hex(),
        "signature": sig.hex(),
    })

print(json.dumps({
    "algorithm": "intent-compact-message",
    "note": "transfer/withdraw compact-message layout + SHA256 (+ ed25519 signature over the hash).",
    "vectors": vectors,
}, indent=2))
