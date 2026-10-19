#!/usr/bin/env python3
"""SendPublicMultiPayment intent vectors (ocp 0.8.0, IntentMultiTransfer on both apps).

Given a source cluster and N payments, both builders must emit:

  actions[i] = NoPrivacyTransfer(id = i, source = sourceVault, authority = sourceAuthority,
                                 destination = payments[i].destination, amount = payments[i].quarks)

in input order, and each action signs the same compact message a single transfer signs
(see compact_message.json), with the nonce and blockhash the server returns for action i:

  "transfer" || source(32) || destination(32) || amount(LE8) || nonce(32) || nonceValue(32)
  signature = ed25519_sign( SHA256(message), sourceAuthority )

The metadata block lists the field values each app asserts through its own proto accessors
(source, mint, and every payment's destination, destination_owner and quarks). The serialized
proto bytes are not compared: protobuf-java and swift-protobuf are each tested against their own
generated types.

Public keys are 32 copies of a seed byte, as in compact_message.json, except sourceAuthority,
which is the real ed25519 public key of signerSeed so the signatures verify against it.
"""
import json, hashlib
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

def pk(seed: int) -> bytes:
    return bytes([seed]) * 32

SIGNER_SEED = bytes([0x01]) * 32
_authority = Ed25519PrivateKey.from_private_bytes(SIGNER_SEED)
AUTHORITY = _authority.public_key().public_bytes(
    serialization.Encoding.Raw, serialization.PublicFormat.Raw)

SOURCE_VAULT_SEED = 0x10
MINT_SEED = 0x0F

def message(source: bytes, dest: bytes, amount: int, nonce: bytes, nonce_value: bytes) -> bytes:
    return b"transfer" + source + dest + int(amount).to_bytes(8, "little") + nonce + nonce_value

VERIFIED_CURRENCY = "usd"
VERIFIED_RATE = "1.0"

def pay(d, o, quarks, currency=VERIFIED_CURRENCY, mint=MINT_SEED):
    # 6-decimal token at rate 1.0: native amount = quarks / 10^6, as a decimal string.
    native = f"{quarks // 1_000_000}.{quarks % 1_000_000:06d}"
    return {"destinationSeed": d, "destinationOwnerSeed": o, "quarks": str(quarks),
            "mintSeed": mint, "currency": currency, "nativeAmount": native}

VALID = [
    # Reaction boost: 80% to the message sender, 20% to the group creator.
    ("boost 80/20", [pay(0x21, 0x31, 800_000), pay(0x22, 0x32, 200_000)]),
    ("two uneven, large amount", [pay(0x21, 0x31, (1 << 63) - 1), pay(0x22, 0x32, 1)]),
    ("ten payments (max)", [pay(0x40 + i, 0x60 + i, 1_000 * (i + 1)) for i in range(10)]),
]

two = lambda: [pay(0x21, 0x31, 800_000), pay(0x22, 0x32, 200_000)]
def with_(i, **kw):
    ps = two(); ps[i].update(kw); return ps

AUTHORITY_SEED_MARKER = "sourceAuthority"  # destinationOwner == the real source authority key

REJECT = [
    ("one payment", "count", [pay(0x21, 0x31, 800_000)]),
    ("eleven payments", "count", [pay(0x40 + i, 0x60 + i, 1_000) for i in range(11)]),
    ("duplicate destination", "duplicateDestination", with_(1, destinationSeed=0x21)),
    ("duplicate destination owner", "duplicateDestinationOwner", with_(1, destinationOwnerSeed=0x31)),
    ("destination is source vault", "destinationIsSource", with_(1, destinationSeed=SOURCE_VAULT_SEED)),
    ("destination owner is source authority", "destinationIsSource",
        with_(1, destinationOwnerSeed=AUTHORITY_SEED_MARKER)),
    ("mint mismatch", "mintMismatch", with_(1, mintSeed=0x0E)),
    ("currency mismatch", "currencyMismatch", with_(1, currency="cad")),
]

def common():
    return {
        "sourceVaultSeed": SOURCE_VAULT_SEED,
        "sourceAuthority": AUTHORITY.hex(),
        "signerSeed": SIGNER_SEED.hex(),
        "mintSeed": MINT_SEED,
        "verifiedState": {"currency": VERIFIED_CURRENCY, "rate": VERIFIED_RATE},
    }

vectors = []
for name, payments in VALID:
    source = pk(SOURCE_VAULT_SEED)
    actions = []
    for i, p in enumerate(payments):
        d, quarks = p["destinationSeed"], int(p["quarks"])
        nonce_seed, nonce_value_seed = 0xA0 + i, 0xC0 + i
        msg = message(source, pk(d), quarks, pk(nonce_seed), pk(nonce_value_seed))
        digest = hashlib.sha256(msg).digest()
        actions.append({
            "id": i,
            "sourceSeed": SOURCE_VAULT_SEED,
            "authority": AUTHORITY.hex(),
            "destinationSeed": d,
            "mintSeed": MINT_SEED,
            "amount": str(quarks),
            "nonceSeed": nonce_seed,
            "nonceValueSeed": nonce_value_seed,
            "message": msg.hex(),
            "sha256": digest.hex(),
            "signature": _authority.sign(digest).hex(),
        })
    vectors.append({"name": name, **common(), "payments": payments,
        "expectedActions": actions,
        "expectedMetadata": {
            "sourceSeed": SOURCE_VAULT_SEED, "mintSeed": MINT_SEED,
            "payments": [{k: p[k] for k in ("destinationSeed", "destinationOwnerSeed", "quarks",
                                            "nativeAmount", "currency")} for p in payments],
        }})

rejections = [{"name": n, **common(), "payments": ps, "expectedError": e} for n, e, ps in REJECT]

print(json.dumps({
    "algorithm": "ocp-send-public-multi-payment",
    "note": "IntentMultiTransfer: one NoPrivacyTransfer per payment in input order, ids 0..n-1, "
            "per-action transfer compact message + SHA256 + ed25519 signature by the source authority. "
            "Every payment carries destination_owner. `rejections` must each make the builder throw; "
            "expectedError names the rule, not a platform error type. A destinationOwnerSeed of "
            "\"sourceAuthority\" means the sourceAuthority key itself.",
    "vectors": vectors,
    "rejections": rejections,
}, indent=2))
