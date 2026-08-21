import CoreGraphics
import Foundation
import Testing
@testable import SharedCoreKit

/// Covers the Swift side of the code renderer: the badge artwork's trip through the path
/// parser, and the figure the geometry resolves to.
@Suite struct KikCodeFigureTests {

    private let payload = Data(repeating: 0xAB, count: 35)

    // MARK: - Badge -

    /// The artwork is a Kotlin constant, so this failing means it changed into something the
    /// parser doesn't cover -- not that a caller passed something bad.
    @Test func badgeArtworkParses() throws {
        let path = try SVGPath.parse(KikCode.Badge.artwork)

        #expect(!path.isEmpty)

        let bounds = path.boundingBox
        // A disc inscribed in the viewport, drawn from its own top-left.
        #expect(abs(bounds.minX) < 0.01)
        #expect(abs(bounds.minY) < 0.01)
        #expect(abs(bounds.width - 61.665) < 0.01)
        #expect(abs(bounds.height - 61.665) < 0.01)
    }

    /// The whole point of the even-odd rule: the glyph is a hole, not a shape.
    @Test func badgeGlyphIsKnockedOut() {
        let path = KikCode.Badge.path

        // Inside the top bar of the F, which spans x 20...44 and y 15...24.6.
        #expect(!path.contains(CGPoint(x: 30, y: 20), using: .evenOdd))
        // Inside the disc, clear of the glyph.
        #expect(path.contains(CGPoint(x: 10, y: 31), using: .evenOdd))
        // Outside the disc entirely.
        #expect(!path.contains(CGPoint(x: 1, y: 1), using: .evenOdd))
    }

    /// The viewport square is what gets centred on the well, exactly as [KikCodeSvg] centres it.
    /// The disc inside it is drawn from the viewport's origin and stops 0.335 short of the far
    /// edge, so the disc itself sits a hair up and to the left -- shared artwork, shared offset.
    @Test func badgeSitsInTheMiddleWell() {
        let dimension: CGFloat = 1024
        let radius = KikCode.badgeRadius(forCodeOfDimension: dimension)
        let bounds = KikCode.Badge.path(forCodeOfDimension: dimension).boundingBox

        let scale = radius * 2 / KikCode.Badge.viewport
        #expect(abs(bounds.width - 61.665 * scale) < 0.01)
        #expect(abs(bounds.minX - (dimension / 2 - radius)) < 0.01)
        #expect(abs(bounds.minY - (dimension / 2 - radius)) < 0.01)
    }

    // MARK: - Figure -

    /// Kotlin's own `require` would raise an exception Swift can't catch, so the guards have
    /// to hold on this side.
    @Test func figureRejectsInputKotlinWouldThrowOn() {
        #expect(throws: KikCode.FigureFailure.self) {
            try KikCode.figure(payload: payload, dimension: 0)
        }
        #expect(throws: KikCode.FigureFailure.self) {
            try KikCode.figure(payload: Data(), dimension: 512)
        }
        #expect(throws: KikCode.FigureFailure.self) {
            let tooLong = Data(repeating: 0xAB, count: KikCode.maxPayloadBytes + 1)
            try KikCode.figure(payload: tooLong, dimension: 512)
        }
    }

    @Test func figureFillsItsBox() throws {
        let dimension: CGFloat = 512
        let figure = try KikCode.figure(payload: payload, dimension: dimension)

        #expect(figure.dimension == dimension)
        #expect(figure.center == CGPoint(x: 256, y: 256))
        #expect(!figure.marks.isEmpty)

        // Marks stay inside the square, and reach most of the way across it: the outermost
        // ring's edge sits at 0.95 of the outer radius plus half a dot.
        let bounds = figure.marks.boundingBox
        #expect(bounds.minX >= 0)
        #expect(bounds.minY >= 0)
        #expect(bounds.maxX <= dimension)
        #expect(bounds.maxY <= dimension)
        #expect(bounds.width > dimension * 0.9)
    }

    @Test func figureLeavesTheWellClearForTheBadge() throws {
        let figure = try KikCode.figure(payload: payload, dimension: 512)

        // Nothing is drawn where the badge goes, which is what lets the badge be opaque.
        #expect(!figure.marks.contains(figure.center))
        #expect(figure.badge?.contains(figure.center, using: .evenOdd) == false) // the glyph
        #expect(figure.badge?.contains(
            CGPoint(x: figure.center.x - figure.badgeRadius * 0.9, y: figure.center.y),
            using: .evenOdd
        ) == true)
    }

    @Test func figureCanLeaveTheBadgeOut() throws {
        let figure = try KikCode.figure(payload: payload, dimension: 512, includeBadge: false)

        #expect(figure.badge == nil)
    }

    /// The figure and the SVG are two renderings of one description; if they disagree about
    /// where the badge goes, an exported code and the code on screen show different logos.
    @Test func figurePlacesTheBadgeWhereTheSvgDoes() throws {
        let dimension: CGFloat = 1024
        let figure = try KikCode.figure(payload: payload, dimension: dimension)
        let svg = KikCode.svg(payload: payload, dimension: Double(dimension))

        let transform = try #require(
            svg.split(separator: "\n").first { $0.contains("fill-rule=\"evenodd\"") }
        )
        let numbers = transform
            .split(whereSeparator: { "() ".contains($0) })
            .compactMap { Double($0) }
        let translate = try #require(numbers.first)
        let scale = try #require(numbers.last)

        let bounds = figure.badge?.boundingBox
        #expect(abs((bounds?.minX ?? 0) - CGFloat(translate)) < 0.01)
        #expect(abs((bounds?.width ?? 0) - CGFloat(scale) * 61.665) < 0.02)
    }
}
