import Foundation
import Testing
import SharedCore
@testable import SharedCoreKit

/// Every facade in this module moves bytes across the Kotlin boundary in both directions, so the
/// two copies get their own tests rather than being covered incidentally by whichever facade test
/// happens to run first.
@Suite struct DataBridgeTests {

    @Test func roundTripsThroughKotlin() {
        let original = Data([0x00, 0x01, 0x7F, 0x80, 0xFF])
        #expect(Data(original.kotlinByteArray) == original)
    }

    @Test func roundTripsEmpty() {
        #expect(Data(Data().kotlinByteArray) == Data())
    }

    @Test func roundTripsEveryByteValue() {
        let original = Data((0...255).map { UInt8($0) })
        #expect(Data(original.kotlinByteArray) == original)
    }
}

@Suite struct DataBridgeThroughputTests {

    /// The size of the two discrete-curve tables the iOS app hands the bridge on first use.
    private static let curveTableByteCount = 6_720_032

    /// A per-element bridge costs one Objective-C message send per byte, which put ~1.9s of the
    /// iOS app's launch inside `SharedBondingCurve.initialize`. The budget is two orders of
    /// magnitude above a bulk copy of this size and an order of magnitude below the per-element
    /// cost, so it fails on a regression rather than on a slow machine.
    @Test func convertsCurveSizedDataWithinBudget() {
        let original = Data(repeating: 0xAB, count: Self.curveTableByteCount)

        let start = Date()
        let kotlin = original.kotlinByteArray
        let round = Data(kotlin)
        let elapsed = Date().timeIntervalSince(start)

        #expect(round.count == original.count)
        #expect(elapsed < 0.2, "round trip of \(Self.curveTableByteCount) bytes took \(elapsed)s")
    }
}
