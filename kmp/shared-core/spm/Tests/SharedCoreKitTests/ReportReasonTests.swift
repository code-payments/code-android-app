import Foundation
import Testing
@testable import SharedCoreKit

@Suite struct ReportReasonTests {

    @Test func buildsTheBareTokenWhenThereAreNoDetails() {
        #expect(ReportDescription.build(reason: .spam, details: nil) == "spam")
    }

    @Test func putsTrimmedDetailsOnTheSecondLine() {
        #expect(
            ReportDescription.build(reason: .scamorfraud, details: "  details  ")
                == "scam_or_fraud\ndetails"
        )
    }

    /// The cap crosses the bridge as an `Int32`; this catches a conversion that mangles it.
    @Test func carriesTheDetailsCapAcrossTheBridge() {
        #expect(ReportDescription.maxDetailsLength == 1000)
    }

    /// The one that earns its keep: a seventh reason added in Kotlin compiles fine on both
    /// sides and would be silently missing from the iOS sheet.
    @Test func displayOrderCoversEveryReason() {
        #expect(ReportReason.displayOrder.count == ReportReason.entries.count)
        #expect(Set(ReportReason.displayOrder.map(\.token)).count == ReportReason.displayOrder.count)
    }
}
