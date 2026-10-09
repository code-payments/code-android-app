import Foundation
import SharedCore
import Testing
@testable import SharedCoreKit

@Suite struct TextFormatTests {

    @Test func stripsTheMarkersAroundBold() {
        let result = TextFormat.parse("*bold*")
        #expect(result.display == "bold")
        #expect(result.spans == [.bold(NSRange(location: 0, length: 4))])
        #expect(result.displayRanges.isEmpty)
    }

    /// Offsets are UTF-16 on both sides; an emoji is two code units and must not shift the span.
    @Test func countsOffsetsInUTF16() {
        let result = TextFormat.parse("😀 *b*")
        #expect(result.display == "😀 b")
        #expect(result.spans == [.bold(NSRange(location: 3, length: 1))])
    }

    @Test func turnsAMaskedLinkIntoALinkOverItsText() {
        let text = "[docs](https://example.com)"
        let url = (text as NSString).range(of: "https://example.com")
        let result = TextFormat.parse(text, ranges: [.init(range: url, kind: .link)])
        #expect(result.display == "docs")
        #expect(result.displayRanges == [
            .init(range: NSRange(location: 0, length: 4), kind: .link, target: URL(string: "https://example.com")),
        ])
    }

    @Test func leavesMarkersInsideAMentionAlone() {
        let text = "@a*b*"
        let result = TextFormat.parse(text, ranges: [.init(range: NSRange(location: 0, length: 5), kind: .mention)])
        #expect(result.display == text)
        #expect(result.spans.isEmpty)
        #expect(result.displayRanges == [.init(range: NSRange(location: 0, length: 5), kind: .mention)])
    }

    /// A style or kind added in Kotlin compiles fine on both sides and would trap in the mapping.
    @Test func mapsEveryKotlinCase() {
        let styles = Set(SharedCore.FormatStyle.entries.map {
            TextFormat.Span(SharedCore.StyledSpan(start: 0, end: 1, style: $0))
        })
        #expect(styles.count == SharedCore.FormatStyle.entries.count)

        let kinds = Set(SharedCore.RangeKind.entries.map(TextFormat.RangeKind.init))
        #expect(kinds.count == SharedCore.RangeKind.entries.count)
    }
}
