import CoreGraphics
import Foundation
import SharedCore

public extension KikCode {

    /// A code resolved into paths ready to fill — the iOS counterpart to Android's
    /// `KikCodePainter` and to [KikCodeSvg], all three fed by the same shared geometry.
    struct Figure {

        /// Side of the square the paths are laid out in.
        public let dimension: CGFloat

        /// Center of the figure, on both axes.
        public let center: CGPoint

        /// Radius of the badge well at the middle.
        public let badgeRadius: CGFloat

        /// Diameter of a dot, and equivalently the width a run is stroked at.
        public let dotDiameter: CGFloat

        /// Every data mark as one path. Fill it; runs arrive already outlined, so there is
        /// nothing left to stroke.
        public let marks: CGPath

        /// The badge in the middle well, or `nil` if it was left out.
        ///
        /// - Important: fill this with the even-odd rule; see ``Badge/path``.
        public let badge: CGPath?
    }

    /// Largest payload a code can carry.
    static var maxPayloadBytes: Int { Int(KikCodeSpec.shared.MAX_PAYLOAD_BYTES) }

    /// Lays `payload` out in a `dimension`-sided square and resolves it to drawable paths.
    ///
    /// - Throws: ``FigureFailure`` if `dimension` isn't positive, or `payload` is empty or
    ///   longer than ``maxPayloadBytes``. Checked here rather than left to Kotlin, whose own
    ///   `require` would raise an exception Swift can't catch.
    static func figure(
        payload: Data,
        dimension: CGFloat,
        includeBadge: Bool = true
    ) throws -> Figure {
        guard dimension > 0 else { throw FigureFailure.invalidDimension(dimension) }
        guard !payload.isEmpty else { throw FigureFailure.emptyPayload }
        guard payload.count <= maxPayloadBytes else {
            throw FigureFailure.payloadTooLong(payload.count, maximum: maxPayloadBytes)
        }

        let description = KikCodeGeometry.shared.describe(
            payload: payload.kotlinByteArray,
            dimension: Double(dimension)
        )

        let center = CGFloat(description.center)
        let dotDiameter = CGFloat(description.dotDiameter)
        let badgeRadius = CGFloat(description.badgeRadius)

        return Figure(
            dimension: CGFloat(description.dimension),
            center: CGPoint(x: center, y: center),
            badgeRadius: badgeRadius,
            dotDiameter: dotDiameter,
            marks: marksPath(description, center: center, dotDiameter: dotDiameter),
            badge: includeBadge ? Badge.path(radius: badgeRadius, center: center) : nil
        )
    }

    /// Why a payload couldn't be laid out.
    enum FigureFailure: Error {
        case invalidDimension(CGFloat)
        case emptyPayload
        case payloadTooLong(Int, maximum: Int)
    }
}

// MARK: - Marks -

private extension KikCode {

    /// Collapses the shared marks into a single fillable path.
    ///
    /// Runs are centerlines widened to `dotDiameter` with round caps, so a run's ends land
    /// exactly where its first and last dots would — the same construction [KikCodeSvg] emits
    /// as a stroked group. Outlining them here lets the whole figure be one fill.
    static func marksPath(
        _ description: KikCodeDescription,
        center: CGFloat,
        dotDiameter: CGFloat
    ) -> CGPath {
        let dotRadius = dotDiameter / 2
        let middle = CGPoint(x: center, y: center)

        let dots = CGMutablePath()
        let centerlines = CGMutablePath()

        for mark in description.marks {
            switch mark {
            case let dot as KikCodeMarkDot:
                dots.addEllipse(in: CGRect(
                    x: CGFloat(dot.x) - dotRadius,
                    y: CGFloat(dot.y) - dotRadius,
                    width: dotDiameter,
                    height: dotDiameter
                ))

            case let ring as KikCodeMarkRing:
                centerlines.addEllipse(in: CGRect(
                    x: center - CGFloat(ring.radius),
                    y: center - CGFloat(ring.radius),
                    width: CGFloat(ring.radius) * 2,
                    height: CGFloat(ring.radius) * 2
                ))

            case let arc as KikCodeMarkArc:
                let radius = CGFloat(arc.radius)
                let start = CGFloat(arc.startRadians)
                let end = start + CGFloat(arc.sweepRadians)
                // Move first: `addArc` would otherwise join this arc to the previous one.
                centerlines.move(to: CGPoint(
                    x: center + radius * cos(start),
                    y: center + radius * sin(start)
                ))
                // `clockwise: false` sweeps in the direction of increasing angle, which is
                // the direction the shared geometry measures its sweeps in.
                centerlines.addArc(
                    center: middle,
                    radius: radius,
                    startAngle: start,
                    endAngle: end,
                    clockwise: false
                )

            default:
                // The mark set is closed on the Kotlin side; a new case is a version skew.
                continue
            }
        }

        let path = CGMutablePath()
        path.addPath(dots)
        if !centerlines.isEmpty {
            path.addPath(centerlines.copy(
                strokingWithWidth: dotDiameter,
                lineCap: .round,
                lineJoin: .round,
                miterLimit: 0
            ))
        }
        return path.copy() ?? path
    }
}
