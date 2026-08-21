import CoreGraphics
import Foundation

/// Turns the SVG path syntax the shared artwork is authored in into a `CGPath`.
///
/// Deliberately not a general SVG implementation. It covers the commands an Android vector
/// drawable emits — `M`, `L`, `H`, `V`, `C`, `S`, `Z`, absolute and relative — and reports
/// anything else rather than guessing, so artwork that outgrows this parser fails a test
/// instead of drawing wrong.
enum SVGPath {

    /// Parses `d`, the value of an SVG `path` element's `d` attribute.
    static func parse(_ d: String) throws -> CGPath {
        let path = CGMutablePath()
        var tokens = Tokenizer(d)

        var command: Character?
        var point: CGPoint = .zero
        var subpathStart: CGPoint = .zero
        // Tracked for `S`, whose first control point mirrors the previous curve's second.
        var lastControl: CGPoint?

        func coordinate(_ command: Character) throws -> Double {
            guard let value = tokens.nextNumber() else { throw Failure.truncatedCommand(command) }
            return value
        }

        /// Reads a point, folding in the current point when `command` is relative (lowercase).
        func nextPoint(_ command: Character) throws -> CGPoint {
            let x = try coordinate(command)
            let y = try coordinate(command)
            guard command.isLowercase else { return CGPoint(x: x, y: y) }
            return CGPoint(x: point.x + x, y: point.y + y)
        }

        while let token = tokens.next() {
            switch token {
            case .command(let character):
                command = character

            case .number(let value):
                // An omitted command repeats the previous one, except that a repeated
                // `moveto` draws lines. Push the number back for the handler below.
                guard let previous = command else { throw Failure.leadingNumber }
                command = (previous == "M") ? "L" : (previous == "m") ? "l" : previous
                tokens.pushBack(value)
            }

            guard let current = command else { throw Failure.leadingNumber }

            switch current {
            case "M", "m":
                point = try nextPoint(current)
                path.move(to: point)
                subpathStart = point
                lastControl = nil

            case "L", "l":
                point = try nextPoint(current)
                path.addLine(to: point)
                lastControl = nil

            case "H", "h":
                let x = try coordinate(current)
                point = CGPoint(x: current.isLowercase ? point.x + x : x, y: point.y)
                path.addLine(to: point)
                lastControl = nil

            case "V", "v":
                let y = try coordinate(current)
                point = CGPoint(x: point.x, y: current.isLowercase ? point.y + y : y)
                path.addLine(to: point)
                lastControl = nil

            case "C", "c":
                let control1 = try nextPoint(current)
                let control2 = try nextPoint(current)
                point = try nextPoint(current)
                path.addCurve(to: point, control1: control1, control2: control2)
                lastControl = control2

            case "S", "s":
                // With no preceding curve the first control point coincides with the current
                // point, which is what the spec asks for.
                let control1 = lastControl.map {
                    CGPoint(x: 2 * point.x - $0.x, y: 2 * point.y - $0.y)
                } ?? point
                let control2 = try nextPoint(current)
                point = try nextPoint(current)
                path.addCurve(to: point, control1: control1, control2: control2)
                lastControl = control2

            case "Z", "z":
                path.closeSubpath()
                point = subpathStart
                lastControl = nil

            default:
                throw Failure.unsupportedCommand(current)
            }
        }

        return path.copy() ?? path
    }

    enum Failure: Error {
        /// A command this parser doesn't implement — most likely `A`, an elliptical arc.
        case unsupportedCommand(Character)
        /// A command ran out of coordinates before it had all of them.
        case truncatedCommand(Character)
        /// Coordinates appeared before any command told us what to do with them.
        case leadingNumber
    }
}

// MARK: - Tokenizer -

private extension SVGPath {

    enum Token {
        case command(Character)
        case number(Double)
    }

    /// Splits path data into commands and numbers.
    ///
    /// SVG lets separators be dropped wherever the split is unambiguous — `20,-0` and
    /// `1.5.5` are each two numbers — so numbers end at the first character that can't
    /// continue them rather than at whitespace.
    struct Tokenizer {

        private let characters: [Character]
        private var index: Int = 0
        private var pushedBack: Double?

        init(_ string: String) {
            characters = Array(string)
        }

        /// Returns a number read ahead of its turn, so a repeated command can re-read it.
        mutating func pushBack(_ value: Double) {
            pushedBack = value
        }

        mutating func next() -> Token? {
            if let value = pushedBack {
                pushedBack = nil
                return .number(value)
            }
            while true {
                skipSeparators()
                guard index < characters.count else { return nil }

                let character = characters[index]
                if character.isLetter {
                    index += 1
                    return .command(character)
                }
                // `scanNumber` steps over anything that starts neither a number nor a
                // command, so this retries rather than ending the stream early.
                if let value = scanNumber() { return .number(value) }
            }
        }

        /// Reads the next number, refusing to step over a command to find one.
        mutating func nextNumber() -> Double? {
            if let value = pushedBack {
                pushedBack = nil
                return value
            }
            skipSeparators()
            guard index < characters.count, !characters[index].isLetter else { return nil }
            return scanNumber()
        }

        private mutating func skipSeparators() {
            while index < characters.count,
                  characters[index] == "," || characters[index].isWhitespace {
                index += 1
            }
        }

        private mutating func scanNumber() -> Double? {
            let start = index

            if index < characters.count, characters[index] == "-" || characters[index] == "+" {
                index += 1
            }
            var sawDot = false
            while index < characters.count {
                let character = characters[index]
                if character.isNumber {
                    index += 1
                } else if character == ".", !sawDot {
                    sawDot = true
                    index += 1
                } else {
                    break
                }
            }
            // An exponent's own sign belongs to the exponent, not to a following number.
            if index < characters.count, characters[index] == "e" || characters[index] == "E" {
                var lookahead = index + 1
                if lookahead < characters.count,
                   characters[lookahead] == "-" || characters[lookahead] == "+" {
                    lookahead += 1
                }
                if lookahead < characters.count, characters[lookahead].isNumber {
                    index = lookahead
                    while index < characters.count, characters[index].isNumber {
                        index += 1
                    }
                }
            }

            guard index > start else {
                // Not a number and not a letter: skip it so a stray character can't spin.
                index += 1
                return nil
            }
            return Double(String(characters[start..<index]))
        }
    }
}
