#!/usr/bin/env python3
"""Generate authoritative BIP39 + SLIP-0010 (ed25519) derivation vectors.

Mirrors what both apps do (verified from Derive.kt/Derive.swift + MnemonicCode/Mnemonic):
  1. BIP39 seed  = PBKDF2-HMAC-SHA512(mnemonic sentence, "mnemonic"+passphrase, 2048, 64 bytes)
  2. SLIP-0010   = master HMAC-SHA512("ed25519 seed", seed); then per index CKDPriv with EVERY index
                   FORCE-HARDENED (apps add 0x80000000 unconditionally, ignoring the path's ' flag)
  3. account key = ed25519 keypair whose seed is the 32-byte derived key
Anchored to the official BIP39 and SLIP-0010 test vectors so "matches fixture" == "correct".
"""
import hashlib, hmac, json
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

HARDENED = 0x80000000
B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def bip39_seed(mnemonic: str, passphrase: str = "") -> bytes:
    return hashlib.pbkdf2_hmac("sha512", mnemonic.encode("utf-8"),
                               ("mnemonic" + passphrase).encode("utf-8"), 2048, 64)

def slip10_master(seed: bytes):
    I = hmac.new(b"ed25519 seed", seed, hashlib.sha512).digest()
    return I[:32], I[32:]

def slip10_ckd(key: bytes, chain: bytes, index: int):  # index already includes hardened bit
    I = hmac.new(chain, b"\x00" + key + index.to_bytes(4, "big"), hashlib.sha512).digest()
    return I[:32], I[32:]

def parse_path(p: str):  # "m/44'/501'/0'/0'/7665'/0" -> [44,501,0,0,7665,0]
    return [int(x.replace("'", "")) for x in p.split("/")[1:]]

def derive_key(seed: bytes, path: str) -> bytes:
    key, chain = slip10_master(seed)
    for v in parse_path(path):
        key, chain = slip10_ckd(key, chain, HARDENED + v)   # FORCE-hardened, like the apps
    return key

def b58encode(b: bytes) -> str:
    n = int.from_bytes(b, "big"); s = ""
    while n > 0:
        n, r = divmod(n, 58); s = B58[r] + s
    return "1" * (len(b) - len(b.lstrip(b"\x00"))) + s

def pub_of(dk: bytes) -> bytes:
    return Ed25519PrivateKey.from_private_bytes(dk).public_key().public_bytes_raw()

# ---- anchors: fail loudly if the reference drifts from the standards ----
# BIP39 (Trezor vector, empty passphrase)
_ab = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
assert bip39_seed(_ab).hex().startswith("5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1"), "BIP39 anchor failed"
# SLIP-0010 ed25519 official vector, seed 000102...0f
_k, _c = slip10_master(bytes.fromhex("000102030405060708090a0b0c0d0e0f"))
assert _k.hex() == "2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7", "SLIP-0010 master anchor failed"
_k0, _c0 = slip10_ckd(_k, _c, HARDENED + 0)
assert _k0.hex() == "68e0fe46dfb67e368c75379acec591dad19df3cde26e63b93a8e704f1dade7a3", "SLIP-0010 m/0' anchor failed"

# ---- app vectors: known valid mnemonics, empty passphrase, real app paths ----
MNEMONICS = {
    "abandon-x11-about": _ab,
    "legal-winner":      "legal winner thank year wave sausage worth useful legal winner thank yellow",
}
PATHS = ["m/44'/501'/0'/0'", "m/44'/501'/0'/0'/7665'/0", "m/44'/501'/0'/0'/2335'/5"]

vectors = []
for mname, mnemonic in MNEMONICS.items():
    seed = bip39_seed(mnemonic)
    for path in PATHS:
        dk = derive_key(seed, path)
        pub = pub_of(dk)
        vectors.append({
            "name": f"{mname} {path}",
            "mnemonic": mnemonic,
            "passphrase": "",
            "path": path,
            "seedBip39": seed.hex(),
            "derivedKey": dk.hex(),      # 32-byte SLIP-0010 key == ed25519 seed
            "publicKey": pub.hex(),
            "address": b58encode(pub),   # Solana address
        })

print(json.dumps({
    "algorithm": "bip39+slip10-ed25519",
    "note": "BIP39 seed -> SLIP-0010 ed25519 (all indices force-hardened) -> ed25519 keypair. "
            "Apps must reproduce publicKey/address for each mnemonic+path.",
    "vectors": vectors,
}, indent=2))
