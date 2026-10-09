import Foundation
import SharedCore

/// Chat text markup (bold, italic, strike, code, code blocks, quotes, lists, masked links), parsed
/// by the shared Kotlin in `:libs:text-format`.
///
/// Offsets in and out are UTF-16 code units, which is what Kotlin `String` indices and `NSRange`
/// both count, so they cross the bridge unchanged.
public enum TextFormat {

    /// What a detected range is. The parser never detects these itself; callers pass them in.
    public enum RangeKind: Hashable, Sendable {
        case link
        case mention
    }

    /// A link or mention that detection found in the raw text. A marker inside one never opens or
    /// closes a style.
    public struct ProtectedRange: Hashable, Sendable {
        public let range: NSRange
        public let kind: RangeKind

        public init(range: NSRange, kind: RangeKind) {
            self.range = range
            self.kind = kind
        }
    }

    /// A style over `FormattedText.display`.
    public enum Span: Hashable, Sendable {
        case bold(NSRange)
        case italic(NSRange)
        case strike(NSRange)
        case code(NSRange)
        case codeBlock(NSRange)
        case quote(NSRange)
        case bullet(NSRange)
        case numbered(NSRange)

        public var range: NSRange {
            switch self {
            case .bold(let range), .italic(let range), .strike(let range), .code(let range),
                 .codeBlock(let range), .quote(let range), .bullet(let range), .numbered(let range):
                return range
            }
        }
    }

    /// An input range moved to `FormattedText.display` offsets. A masked link `[text](url)`
    /// becomes a `.link` over `text` with the URL as `target`. A range without a `target` opens
    /// its own text.
    public struct DisplayRange: Hashable, Sendable {
        public let range: NSRange
        public let kind: RangeKind
        public let target: URL?

        public init(range: NSRange, kind: RangeKind, target: URL? = nil) {
            self.range = range
            self.kind = kind
            self.target = target
        }
    }

    /// The parser's result. `display` is the raw text minus the consumed markers. `spans` are
    /// merged per style and ordered by start, then longest first. `displayRanges` are ordered by
    /// start, end, kind, then target.
    public struct FormattedText: Hashable, Sendable {
        public let display: String
        public let spans: [Span]
        public let displayRanges: [DisplayRange]
    }

    /// Parses `text`. `ranges` are the links and mentions detection found in it.
    public static func parse(_ text: String, ranges: [ProtectedRange] = []) -> FormattedText {
        let result = SharedCore.TextFormatParserKt.parseTextFormat(
            text: text,
            ranges: ranges.map {
                SharedCore.ProtectedRange(
                    start: Int32($0.range.location),
                    end: Int32(NSMaxRange($0.range)),
                    kind: $0.kind.kotlin
                )
            }
        )
        return FormattedText(
            display: result.display,
            spans: result.spans.map(Span.init),
            displayRanges: result.displayRanges.map(DisplayRange.init)
        )
    }
}

extension TextFormat.RangeKind {

    var kotlin: SharedCore.RangeKind {
        switch self {
        case .link: return .link
        case .mention: return .mention
        }
    }

    // Kotlin enums arrive as classes, so the switch cannot be exhaustive. A case added in Kotlin
    // without one here is a programming error, and `TextFormatTests` walks `entries` to catch it.
    init(_ kind: SharedCore.RangeKind) {
        switch kind {
        case .link: self = .link
        case .mention: self = .mention
        default: preconditionFailure("Unmapped RangeKind \(kind.name)")
        }
    }
}

extension TextFormat.Span {

    init(_ span: SharedCore.StyledSpan) {
        let range = NSRange(location: Int(span.start), length: Int(span.end - span.start))
        switch span.style {
        case .bold: self = .bold(range)
        case .italic: self = .italic(range)
        case .strike: self = .strike(range)
        case .code: self = .code(range)
        case .codeblock: self = .codeBlock(range)
        case .quote: self = .quote(range)
        case .bullet: self = .bullet(range)
        case .numbered: self = .numbered(range)
        default: preconditionFailure("Unmapped FormatStyle \(span.style.name)")
        }
    }
}

extension TextFormat.DisplayRange {

    init(_ range: SharedCore.DisplayRange) {
        self.init(
            range: NSRange(location: Int(range.start), length: Int(range.end - range.start)),
            kind: TextFormat.RangeKind(range.kind),
            target: range.target.flatMap(URL.init(string:))
        )
    }
}
