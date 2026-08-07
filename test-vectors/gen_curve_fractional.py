#!/usr/bin/env python3
"""Fractional (sell-path) + rounding-tie bonding-curve vectors.

The whole-token vectors in gen_curve.py don't exercise the residual divergence risk the map flagged:
- The sell path feeds FRACTIONAL supply/tokens (quarks / 10^10 -> fractional tokens).
- iOS applies its `Rounding(.toNearestOrEven, 50)` context to `endSupply - endStepBoundary` and the
  partial multiplies; Android uses exact `BigDecimal.subtract` / 50-digit HALF_EVEN.

Reference here is EXACT rational arithmetic (`fractions.Fraction`) — the true mathematical value.
All chosen inputs are quark-aligned (<=10 decimals), so the exact value fits well within 50 significant
digits; both apps' BigDecimal (HALF_EVEN, 50) should therefore reproduce it EXACTLY. If either rounds
away from this value, the gate fails and the divergence is caught.
"""
import json, struct
from fractions import Fraction
from decimal import Decimal, getcontext

getcontext().prec = 120
SCALE = 10 ** 18
STEP = 100
BIN_DIR = "../code-ios-app/FlipcashCore/Sources/FlipcashCore/Resources"

def load_table(name):
    with open(f"{BIN_DIR}/{name}.bin", "rb") as f:
        data = f.read()
    return [((h << 64) | l) for l, h in
            (struct.unpack_from("<QQ", data, i * 16) for i in range(len(data) // 16))]

price = load_table("discrete_pricing_table")
cumul = load_table("discrete_cumulative_table")

def ttv_raw(S: Fraction, T: Fraction) -> Fraction:
    start = int(S // STEP)          # floor (S >= 0)
    end = int((S + T) // STEP)
    if start == end:
        return Fraction(price[start]) * T
    partial_start = Fraction((start + 1) * STEP) - S
    partial_end = (S + T) - Fraction(end * STEP)
    middle = Fraction(cumul[end] - cumul[start + 1])
    return Fraction(price[start]) * partial_start + middle + Fraction(price[end]) * partial_end

def frac_to_str(fr: Fraction) -> str:
    d = Decimal(fr.numerator) / Decimal(fr.denominator)   # exact: denominator is a power of ten
    return format(d.normalize(), "f")

# (name, currentSupply, tokens, why). Both within-step and multi-step fractional cases.
# The within-step cases (12.5, one-quark) previously exposed an Android bug (BigDecimal `==` scale
# sensitivity in the startStep==endStep check) that returned a negative value; fixed in
# DiscreteBondingCurve.kt (compareTo), so they're back in the gate.
CASES = [
    ("frac within-step",        "0",              "12.5",             "fractional tokens inside step 0 (regressed Android)"),
    ("frac boundary-cross",     "0",              "150.5",            "fractional partial end after a full step"),
    ("frac both ends",          "50.25",          "100.5",            "fractional start AND end partial"),
    ("sell-path multi-step",    "0",              "12345.6789012345", "value(0..new_supply): fractional, many steps"),
    ("high-supply fractional",  "999950.123456789","100.987654321",   "fractional crossing a boundary at high price"),
    ("one-quark token",         "0",              "0.0000000001",     "1 token-quark (10^-10): within-step, sub-micro"),
]

vectors = []
for name, s_str, t_str, why in CASES:
    S, T = Fraction(s_str), Fraction(t_str)
    raw = ttv_raw(S, T)
    vectors.append({
        "name": name,
        "note": why,
        "currentSupply": s_str,     # decimal strings (fractional) -> use the BigDecimal overloads
        "tokens": t_str,
        "value": frac_to_str(raw / SCALE),
    })

print(json.dumps({
    "algorithm": "discrete-bonding-curve-fractional",
    "units": "currentSupply & tokens are fractional whole-token decimal strings; value in USDC",
    "note": "Exact rational reference. Exercises the sell-path fractional arithmetic + rounding edges.",
    "vectors": vectors,
}, indent=2))
