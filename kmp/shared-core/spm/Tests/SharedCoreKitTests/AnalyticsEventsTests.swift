import Testing
@testable import SharedCoreKit

@Suite struct AnalyticsEventsTests {

    @Test func flattensEachValueTypeToASwiftScalar() {
        let event: TrackedEvent = ChatEvents.shared.sentMessage(chatType: .group, error: "e")
        #expect(event.name == "Sent Message")
        #expect(event.scalarProperties == ["Chat Type": .text("Group"), "Error": .text("e")])
    }

    @Test func carriesNumbersAndFlagsAsTheirOwnTypes() {
        let event = ScanEvents.shared.galleryFailed(timeMillis: 1500, exhausted: true)
        #expect(event.scalarProperties["Time"] == .number(1500))
        #expect(event.scalarProperties["Exhausted"] == .flag(true))
    }

    @Test func omitsAbsentOptionalProperties() {
        #expect(ChatEvents.shared.sentMessage(chatType: .tip, error: nil).scalarProperties.keys.sorted() == ["Chat Type"])
    }

    @Test func buildsAnAmountBearingEventFromSwift() {
        let amount = AnalyticsAmount(fiat: 5, currency: "USD", usdc: 5, quarks: 5_000_000, exchangeRate: nil, mint: nil)
        let event = TransferEvents.shared.sentTip(state: EventState.success, amount: amount, error: nil)
        #expect(event.scalarProperties["State"] == .text("Success"))
        #expect(event.scalarProperties["Quarks"] == .number(5_000_000))
    }

    @Test func tapsAButtonUnderItsFacadeName() {
        #expect(ButtonEvents.shared.tapped(button: AnalyticsButton.shareTokenInfo).name == "Button: Share Token Info")
    }

    /// A value added in Kotlin compiles on both sides and would be silently missing from any
    /// Swift switch that lists the cases; this is the check that it is not.
    @Test func tokenInfoSourcesAreTheFiveTheSpecNames() {
        #expect(TokenInfoSource.entries.map(\.value) == ["Deeplink", "Wallet", "Discovery", "Chat", "Chat Gate"])
    }
}
