import Foundation
import SharedCore

/// Discrete bonding-curve pricing engine, backed by the shared Kotlin implementation in
/// `:libs:currency-math:discrete-curve`. Amounts cross the boundary as decimal strings; callers
/// convert to/from their own `BigDecimal` immediately.
///
/// Kotlin's `SharedBondingCurve` (the facade `object` in
/// `com.flipcash.libs.currency.math.curve`) exports as an ObjC/Swift class of the same name
/// (`SharedCoreSharedBondingCurve`, `swift_name("SharedBondingCurve")`), accessed through its
/// `.shared` singleton -- not as `<FileName>Kt` top-level functions, since it is a Kotlin `object`
/// rather than file-level functions. That collides with this wrapper's own name, so every call
/// below qualifies the generated type as `SharedCore.SharedBondingCurve` to disambiguate.
public enum SharedBondingCurve {

    public static func initialize(pricingTableBytes: Data, cumulativeTableBytes: Data) {
        SharedCore.SharedBondingCurve.shared.initialize(
            pricingTableBytes: pricingTableBytes.kotlinByteArray,
            cumulativeTableBytes: cumulativeTableBytes.kotlinByteArray
        )
    }

    public static func spotPriceAtSupply(supply: Int32) -> String? {
        SharedCore.SharedBondingCurve.shared.spotPriceAtSupply(supply: supply)
    }

    public static func tokensToValue(currentSupply: String, tokens: String) -> String? {
        SharedCore.SharedBondingCurve.shared.tokensToValue(currentSupply: currentSupply, tokens: tokens)
    }

    public static func valueToTokens(currentSupply: Int32, value: String) -> String? {
        SharedCore.SharedBondingCurve.shared.valueToTokens(currentSupply: currentSupply, value: value)
    }

    public static func tokensForValueExchange(currentValue: String, value: String) -> (tokens: String, fx: String)? {
        guard let result = SharedCore.SharedBondingCurve.shared.tokensForValueExchange(currentValue: currentValue, value: value) else {
            return nil
        }
        return (result.tokens, result.fx)
    }

    public static func preciseSupplyFromValue(value: String) -> String {
        SharedCore.SharedBondingCurve.shared.preciseSupplyFromValue(value: value)
    }

    /// `tvlQuarks` is USDC-quarks (USDC's own 6-decimal smallest unit); the shared engine divides
    /// by that fixed unit internally rather than a caller-supplied token decimal count. Crosses as
    /// a plain non-optional `Int64` -- the underlying Kotlin `supplyFromTVL(tvlQuarks: Long): Long`
    /// always resolves to a step index (it snaps out-of-range input to the nearest valid step
    /// rather than failing), so there is no nullable/boxed case to unwrap here.
    public static func supplyFromTVL(tvlQuarks: Int64) -> Int64 {
        SharedCore.SharedBondingCurve.shared.supplyFromTVL(tvlQuarks: tvlQuarks)
    }

    public static func formattedTable() -> String {
        SharedCore.SharedBondingCurve.shared.formattedTable()
    }
}
