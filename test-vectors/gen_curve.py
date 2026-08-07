#!/usr/bin/env python3
"""Generate authoritative discrete-bonding-curve vectors.

Ground truth = the on-chain Rust curve (flipcash-program/api). Both apps load the SAME binary tables
(discrete_pricing_table.bin / discrete_cumulative_table.bin, bit-identical SHA-256), which are
generated from the Rust table.rs. For WHOLE-TOKEN inputs `tokensToValue` is EXACT integer arithmetic
on the raw u128 table values (18-decimal fixed point) — no rounding — so we replicate it precisely
here and anchor to the documented Rust unit-test expectations.

tokensToValue(S, T):
  start = S//100 ; end = (S+T)//100
  if start == end:  raw = price[start] * T
  else:             raw = price[start]*((start+1)*100 - S)
                        + (cumul[end] - cumul[start+1])          # full middle steps
                        + price[end]*((S+T) - end*100)           # partial end step
  value_usdc = raw / 10**18   (exact; denominator is a power of ten)
"""
import json, struct

SCALE = 10 ** 18
STEP = 100
# Both apps read the identical file; use the iOS copy as the shared source.
BIN_DIR = "../code-ios-app/FlipcashCore/Sources/FlipcashCore/Resources"

def load_table(name):
    with open(f"{BIN_DIR}/{name}.bin", "rb") as f:
        data = f.read()
    n = len(data) // 16
    out = [0] * n
    for i in range(n):
        low, high = struct.unpack_from("<QQ", data, i * 16)  # little-endian (low64, high64)
        out[i] = (high << 64) | low
    return out

price = load_table("discrete_pricing_table")
cumul = load_table("discrete_cumulative_table")

def tokens_to_value_raw(S, T):
    start = S // STEP
    end = (S + T) // STEP
    if start == end:
        return price[start] * T
    partial_start = (start + 1) * STEP - S
    partial_end = (S + T) - end * STEP
    middle = cumul[end] - cumul[start + 1]
    return price[start] * partial_start + middle + price[end] * partial_end

def raw_to_decimal(raw):
    """Exact decimal string of raw/10**18 (denominator is a power of ten)."""
    whole, frac = divmod(raw, SCALE)
    if frac == 0:
        return str(whole)
    s = f"{frac:018d}".rstrip("0")
    return f"{whole}.{s}"

# ---- anchors against the documented Rust ground truth ----
assert price[0] == 10 ** 16, f"price[0] anchor failed: {price[0]}"          # 0.01 USDC
assert cumul[0] == 0 and cumul[1] == 10 ** 18, "cumulative anchor failed"    # cumul[1] = price[0]*100
assert tokens_to_value_raw(0, 100) == price[0] * 100, "exact-step anchor"
# Rust test_discrete_tokens_to_value_multiple_steps_with_partials (supply=75, tokens=350)
_exp = price[0]*25 + price[1]*100 + price[2]*100 + price[3]*100 + price[4]*25
assert tokens_to_value_raw(75, 350) == _exp, "multi-step anchor failed"

# (currentSupply, tokens, why) — chosen to exercise divergence points
CASES = [
    (0, 50, "within single step"),
    (0, 100, "exact step boundary"),
    (50, 50, "start mid-step, end on boundary (zero end-partial)"),
    (50, 150, "partial start + full step + boundary end"),
    (75, 350, "multi-step with both partials (Rust test)"),
    (0, 200, "two full steps (cumulative subtraction)"),
    (99, 1, "cross a step boundary buying 1"),
    (100, 1, "exactly at boundary, buy 1 (single step)"),
    (1_000_000, 500, "high supply: cumulative entries exceed u64 (iOS slow path)"),
    (20_999_900, 100, "final step near max supply (21,000,000)"),
]

vectors = []
for S, T, why in CASES:
    raw = tokens_to_value_raw(S, T)
    spot_raw = price[S // STEP]
    vectors.append({
        "name": f"supply={S} tokens={T}",
        "note": why,
        "currentSupply": S,
        "tokens": T,
        "spotPrice": raw_to_decimal(spot_raw),
        "value": raw_to_decimal(raw),        # USDC (exact decimal)
        "valueScaled": str(raw),             # raw u128 (value * 10**18) — unambiguous
    })

print(json.dumps({
    "algorithm": "discrete-bonding-curve",
    "units": "currentSupply & tokens in whole tokens; spotPrice & value in USDC (18-dp fixed point on-chain)",
    "note": "tokensToValue is exact integer arithmetic on the shared u128 tables; ground truth = Rust api/curve.rs.",
    "vectors": vectors,
}, indent=2))
