#!/usr/bin/env python3
"""Authoritative Solana legacy-message serialization vectors.

Implements the canonical legacy-message wire format that both apps' LegacyMessage.encode() must
produce (verified from LegacyMessage.swift / LegacyMessage.kt):

  message = header(3B) || shortvec(account_pubkeys) || recent_blockhash(32B) || shortvec(instructions)
  header  = [numRequiredSignatures, numReadonlySigners, numReadonlyNonSigners]
  account order: payer first, then signers-before-nonsigners, writable-before-readonly, lex(pubkey)
  compiled instruction = [programIndex] || shortvec(accountIndexes) || shortvec(data)
  shortvec length = compact-u16 (7 bits/byte, high bit = continuation)

Inputs are fixed (seed-byte pubkeys, zero blockhash) so both apps reconstruct the same message and
assert byte-equality against these expected bytes.
"""
import json

def compact_u16(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)

def shortvec(items):  # items: list[bytes]
    body = b"".join(items)
    return compact_u16(len(items)) + body

def pubkey(seed: int) -> bytes:
    return bytes([seed]) * 32

# role -> (isSigner, isWritable, isPayer)
ROLES = {
    "payer":             (True,  True,  True),
    "writable":          (False, True,  False),
    "writable-signer":   (True,  True,  False),
    "readonly":          (False, False, False),
    "readonly-signer":   (True,  False, False),
    "readonly-program":  (False, False, False),
}

def build_message(accounts, blockhash_seed, instructions):
    # accounts: list of (seed, role). instructions: list of (program_seed, [account_seeds], data_bytes)
    metas = [(pubkey(s), *ROLES[r]) for s, r in accounts]
    # canonical sort: payer first, signer, writable, then lex by pubkey
    metas.sort(key=lambda m: (not m[3], not m[1], not m[2], m[0]))
    order = [m[0] for m in metas]  # sorted pubkeys

    num_sigs      = sum(1 for m in metas if m[1])
    ro_signers    = sum(1 for m in metas if m[1] and not m[2])
    ro_nonsigners = sum(1 for m in metas if not m[1] and not m[2])
    header = bytes([num_sigs, ro_signers, ro_nonsigners])

    compiled = []
    for prog_seed, acc_seeds, data in instructions:
        prog_index = order.index(pubkey(prog_seed))
        acc_indexes = bytes(order.index(pubkey(s)) for s in acc_seeds)
        compiled.append(bytes([prog_index]) + shortvec([bytes([b]) for b in acc_indexes]) + shortvec([bytes([b]) for b in data]))

    message = header + shortvec(order) + pubkey(blockhash_seed) + shortvec(compiled)
    return header, message

VECTORS_SPEC = [
    {
        "name": "single instruction, 4 accounts",
        "accounts": [(1, "payer"), (2, "writable"), (3, "readonly"), (9, "readonly-program")],
        "blockhashSeed": 0,
        "instructions": [(9, [1, 2, 3], [1, 2, 3])],
    },
    {
        "name": "minimal: payer + program, empty data",
        "accounts": [(1, "payer"), (9, "readonly-program")],
        "blockhashSeed": 7,
        "instructions": [(9, [1], [])],
    },
    {
        "name": "readonly co-signer + two instructions",
        "accounts": [(1, "payer"), (4, "readonly-signer"), (2, "writable"), (9, "readonly-program")],
        "blockhashSeed": 0,
        "instructions": [(9, [1, 2], [255]), (9, [4, 1], [16, 32])],
    },
]

vectors = []
for spec in VECTORS_SPEC:
    header, message = build_message(spec["accounts"], spec["blockhashSeed"], spec["instructions"])
    vectors.append({
        "name": spec["name"],
        "accounts": [{"seed": s, "role": r} for s, r in spec["accounts"]],
        "blockhashSeed": spec["blockhashSeed"],
        "instructions": [
            {"programSeed": p, "accountSeeds": a, "data": bytes(d).hex()} for p, a, d in spec["instructions"]
        ],
        "expectedHeader": header.hex(),
        "expectedMessage": message.hex(),
    })

print(json.dumps({
    "algorithm": "solana-legacy-message",
    "note": "Pubkey(seed) = 32 bytes each == seed. Both apps build the message from these inputs and "
            "must reproduce expectedMessage from LegacyMessage.encode().",
    "vectors": vectors,
}, indent=2))
