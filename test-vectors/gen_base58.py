#!/usr/bin/env python3
"""Generate authoritative Base58 (Bitcoin/Solana alphabet) test vectors.

Base58 is deterministic; this is the reference encoder. Anchored to well-known values
(32 zero bytes -> the Solana system-program address "111...1", 32 ones) and cross-linked to the
ed25519 public keys (which ARE 32-byte Solana addresses). Both apps must reproduce encode+decode.
"""
import json

ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def b58encode(b: bytes) -> str:
    n = int.from_bytes(b, "big")
    s = ""
    while n > 0:
        n, r = divmod(n, 58)
        s = ALPHABET[r] + s
    pad = len(b) - len(b.lstrip(b"\x00"))   # leading zero bytes -> leading '1's
    return "1" * pad + s

# (name, input bytes as hex)
CASES = [
    ("empty", ""),
    ("single-zero", "00"),
    ("zeros-32", "00" * 32),                                  # -> Solana system program address
    ("hello-world", "48656c6c6f20576f726c64"),               # "Hello World"
    ("leading-zeros", "0000287fb4cd"),
    # cross-link: ed25519 rfc8032 public keys, i.e. real 32-byte Solana addresses
    ("pubkey-rfc8032-1", "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
    ("pubkey-rfc8032-3", "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025"),
]

vectors = []
for name, hexin in CASES:
    b = bytes.fromhex(hexin)
    enc = b58encode(b)
    vectors.append({"name": name, "bytes": hexin, "base58": enc})

# sanity anchor: 32 zero bytes must be the canonical Solana all-ones address
assert b58encode(bytes(32)) == "1" * 32, "base58 anchor failed"

print(json.dumps({
    "algorithm": "base58",
    "alphabet": ALPHABET,
    "note": "Bitcoin/Solana Base58. Both apps must encode(bytes)==base58 and decode(base58)==bytes.",
    "vectors": vectors,
}, indent=2))
