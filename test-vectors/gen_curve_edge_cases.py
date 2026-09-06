#!/usr/bin/env python3
"""Edge/rounding-boundary vectors for valueToTokens and tokensForValueExchange.

Neither gen_curve.py nor gen_curve_fractional.py exercises these two functions -- both only check
tokensToValue. This script replicates their exact raw-integer binary-search semantics (the domain
the new shared KMP engine uses, matching iOS's prior UInt128 approach).

Unlike gen_curve.py/gen_curve_fractional.py's tokensToValue (exact integer arithmetic, no rounding
needed), valueToTokens and tokensForValueExchange perform genuine division (by a step price that
isn't a power of ten), and tokensForValueExchange subtracts two independently-derived supply values
that can be nearly equal (catastrophic cancellation). Both engines round EVERY arithmetic operation
-- not just the final result -- to 50 significant digits, half-even (Android's prior
`MathContext(50, HALF_EVEN)`, iOS's prior `Rounding(.toNearestOrEven, 50)`, this module's
`curveDecimalMode`). A reference computed with exact/arbitrary-precision math and rounded only once
at the end does NOT reproduce a correct engine's output for these two functions: chained per-op
rounding and single-final-rounding diverge once cancellation or compounding rounding is in play.

So this script uses Python's own `decimal.Decimal` under a context fixed at 50-significant-digit
precision with ROUND_HALF_EVEN, and performs the SAME sequence of individual add/subtract/multiply/
divide operations the Kotlin engine does (mirroring DiscreteCurveEngine.valueToTokens/
preciseSupplyFromValue/tokensForValueExchange step-for-step). This replicates the documented
arithmetic *contract* (context-precision decimal arithmetic, a language-independent specification
both bignum and Python's decimal module implement) with an independent decimal library -- it is not
copying Kotlin's computed output.
"""
import json, struct
from decimal import Decimal, getcontext, ROUND_HALF_EVEN

getcontext().prec = 50
getcontext().rounding = ROUND_HALF_EVEN

SCALE = 10 ** 18
SCALE_DEC = Decimal(SCALE)
STEP = 100
BIN_DIR = "../code-ios-app/FlipcashCore/Sources/FlipcashCore/Resources"

def load_table(name):
    with open(f"{BIN_DIR}/{name}.bin", "rb") as f:
        data = f.read()
    return [((h << 64) | l) for l, h in
            (struct.unpack_from("<QQ", data, i * 16) for i in range(len(data) // 16))]

price = load_table("discrete_pricing_table")
cumul = load_table("discrete_cumulative_table")
TABLE_LEN = len(price)

def dec_to_str(d: Decimal) -> str:
    return format(d.normalize(), "f")

def from_scaled(raw: int) -> Decimal:
    """Raw 18-decimal fixed-point table entry -> human-scale Decimal. Mirrors fromScaledBigInteger --
    dividing by a power of ten is always exact, so this never actually needs to round."""
    return Decimal(raw) / SCALE_DEC

def to_scaled_int(value: Decimal) -> int:
    """value * SCALE (rounded to context precision, matching toScaledBigInteger's `multiply(..,
    curveDecimalMode)`), then truncated toward zero -- matches toScaledBigInteger/toScaledU128."""
    if value <= 0:
        return 0
    scaled = value * SCALE_DEC
    return int(scaled)  # Decimal -> int truncates toward zero, matching substringBefore('.')

def binary_search_raw(lo, hi, target, table):
    """Largest index in [lo, hi] where table[index] <= target."""
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if table[mid] <= target:
            lo = mid
        else:
            hi = mid - 1
    return lo

def value_to_tokens(current_supply: int, value: Decimal):
    """Returns None if at/beyond max supply. Mirrors DiscreteCurveEngine.valueToTokens step-for-step,
    including its per-operation rounding (every +/-/*// below happens under the 50-sig-fig context)."""
    if value < 0 or current_supply < 0:
        return None
    if value == 0:
        return Decimal(0)

    start_step = current_supply // STEP
    if start_step >= TABLE_LEN - 1:
        return None

    start_step_boundary = (start_step + 1) * STEP
    tokens_to_complete_start = start_step_boundary - current_supply
    start_price = from_scaled(price[start_step])
    cost_to_complete_start = Decimal(tokens_to_complete_start) * start_price

    if value < cost_to_complete_start:
        return value / start_price

    remaining_after_start = value - cost_to_complete_start
    base_cumulative = from_scaled(cumul[start_step + 1])
    target_cumulative = base_cumulative + remaining_after_start
    target_scaled = to_scaled_int(target_cumulative)

    end_step = binary_search_raw(start_step + 1, TABLE_LEN - 1, target_scaled, cumul)
    if end_step >= TABLE_LEN:
        return None

    end_step_supply = end_step * STEP
    tokens_from_complete = end_step_supply - start_step_boundary

    cumulative_at_end = from_scaled(cumul[end_step])
    value_used = cumulative_at_end - base_cumulative
    remaining_value = remaining_after_start - value_used

    end_price = from_scaled(price[end_step])
    tokens_in_end_step = remaining_value / end_price

    return Decimal(tokens_to_complete_start) + Decimal(tokens_from_complete) + tokens_in_end_step

def precise_supply_from_value(value: Decimal) -> Decimal:
    """Mirrors DiscreteCurveEngine.preciseSupplyFromValue step-for-step."""
    if value <= 0:
        return Decimal(0)
    value_scaled = to_scaled_int(value)
    step_index = binary_search_raw(0, TABLE_LEN - 1, value_scaled, cumul)
    step_supply = step_index * STEP

    cumulative_at_step = from_scaled(cumul[step_index])
    remaining_value = value - cumulative_at_step
    if remaining_value <= 0:
        return Decimal(step_supply)

    price_at_step = from_scaled(price[step_index])
    if price_at_step <= 0:
        return Decimal(step_supply)

    fractional_tokens = remaining_value / price_at_step
    capped = min(fractional_tokens, Decimal(STEP))
    return Decimal(step_supply) + capped

def tokens_for_value_exchange(current_value: Decimal, value: Decimal):
    """Mirrors DiscreteCurveEngine.tokensForValueExchange step-for-step -- including subtracting two
    independently-rounded preciseSupplyFromValue results, which is where cancellation can eat
    significant digits relative to a naive exact-then-round-once reference."""
    if value <= 0 or current_value < 0 or value > current_value:
        return None
    new_value = current_value - value
    current_supply = precise_supply_from_value(current_value)
    new_supply = precise_supply_from_value(new_value)
    tokens = current_supply - new_supply
    if tokens <= 0:
        return None
    fx = value / tokens
    return tokens, fx

# ---- anchors ----
assert value_to_tokens(0, Decimal(1)) == Decimal(100), "supply=0 value=1 anchor (spot price 0.01)"
assert tokens_for_value_exchange(Decimal("100"), Decimal("50"))[0] > 0, "basic exchange sanity"

value_to_tokens_cases = [
    ("v2t supply=0 value=0", "0", "0", "zero value must return zero tokens, not error"),
    ("v2t supply=0 value=0.005", "0", "0.005", "sub-step-cost value: cheaper than completing step 0"),
    ("v2t supply=0 value=1", "0", "1", "exact step-boundary value (100 tokens at 0.01)"),
    ("v2t supply=50 value=0.5", "50", "0.5", "start mid-step, value completes the step exactly"),
    ("v2t supply=0 value=1000000", "0", "1000000", "large value spanning many steps"),
    ("v2t near-max-supply", str((TABLE_LEN - 2) * STEP), "0.01", "one step short of the table's end"),
    ("v2t at-max-supply", str((TABLE_LEN - 1) * STEP), "0.01", "at the last valid step -- expect null (at max supply)"),
]

vectors_v2t = []
for name, supply_str, value_str, why in value_to_tokens_cases:
    supply = int(supply_str)
    value = Decimal(value_str)
    result = value_to_tokens(supply, value)
    vectors_v2t.append({
        "name": name,
        "note": why,
        "currentSupply": supply,
        "value": value_str,
        "tokens": dec_to_str(result) if result is not None else None,
    })

tfve_cases = [
    ("tfve basic", "100", "50", "ordinary partial exchange, well within supply"),
    ("tfve full drain", "1", "1", "value equals currentValue exactly -- new supply must land at zero"),
    ("tfve small value", "1000", "0.0000000001", "smallest representable quark-aligned value"),
    ("tfve large tvl", "1000000", "500000", "high-TVL exchange, crosses many steps"),
]

vectors_tfve = []
for name, current_value_str, value_str, why in tfve_cases:
    current_value = Decimal(current_value_str)
    value = Decimal(value_str)
    result = tokens_for_value_exchange(current_value, value)
    vectors_tfve.append({
        "name": name,
        "note": why,
        "currentValue": current_value_str,
        "value": value_str,
        "tokens": dec_to_str(result[0]) if result is not None else None,
        "fx": dec_to_str(result[1]) if result is not None else None,
    })

print(json.dumps({
    "algorithm": "discrete-bonding-curve-edge-cases",
    "units": "currentSupply in whole tokens; value/currentValue/tokens/fx as decimal strings",
    "note": "Reference for valueToTokens and tokensForValueExchange -- the binary-search paths not "
            "covered by curve.json/curve_fractional.json. Computed with Python's own decimal.Decimal "
            "under a 50-significant-digit, half-even context (matching both engines' curveDecimalMode/"
            "MathContext(50, HALF_EVEN) contract), applying the same per-operation rounding sequence "
            "as DiscreteCurveEngine, not exact math rounded once at the end. A null `tokens` means the "
            "operation is expected to fail (nil/None) on both platforms.",
    "valueToTokens": vectors_v2t,
    "tokensForValueExchange": vectors_tfve,
}, indent=2))
