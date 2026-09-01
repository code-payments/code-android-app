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
