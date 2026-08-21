import CoreGraphics
import Foundation
import SharedCore

public extension KikCode {

    /// The Flipcash badge that sits in a code's middle well.
    ///
    /// The artwork is shared: Kotlin carries it as one SVG path — the same one [KikCodeSvg]
    /// embeds in an export and Android's `ic_logo_round_white` draws — and this hands iOS a
    /// `CGPath` of it. Drawing this rather than a bundled image is what keeps the code on
    /// screen, the exported PNG, the exported SVG, and Android all showing one figure.
    enum Badge {

        /// Side of the square viewport ``path`` is authored in.
        public static var viewport: CGFloat { CGFloat(KikCodeBadge.shared.VIEWPORT) }

        /// The shared artwork, as the SVG path data ``path`` is parsed from.
        static var artwork: String { KikCodeBadge.shared.PATH_DATA }

        /// The badge in viewport coordinates: a disc with the glyph knocked out of it.
        ///
        /// - Important: fill this with the even-odd rule. Filled non-zero, the glyph fills in
        ///   solid and the badge is a plain white disc.
        public static let path: CGPath = {
            // The artwork is a compile-time constant on the Kotlin side, so a parse failure
            // is a change to that constant, not bad input — `badgeArtworkParses` guards it.
            (try? SVGPath.parse(artwork)) ?? CGMutablePath()
        }()

        /// The badge sized and placed for a code laid out in a `dimension`-sided square, in
        /// that code's coordinates.
        ///
        /// Independent of the payload: every code reserves the same middle well.
        public static func path(forCodeOfDimension dimension: CGFloat) -> CGPath {
            path(radius: KikCode.badgeRadius(forCodeOfDimension: dimension), center: dimension / 2)
        }

        /// The badge drawn to `radius` about `center`, on both axes.
        static func path(radius: CGFloat, center: CGFloat) -> CGPath {
            let scale = (radius * 2) / viewport
            var transform = CGAffineTransform(translationX: center - radius, y: center - radius)
                .scaledBy(x: scale, y: scale)
            return path.copy(using: &transform) ?? path
        }
    }

    /// Radius of the middle well in a code laid out in a `dimension`-sided square.
    static func badgeRadius(forCodeOfDimension dimension: CGFloat) -> CGFloat {
        dimension * CGFloat(KikCodeSpec.shared.OUTER_RATIO) * CGFloat(KikCodeSpec.shared.INNER_RING_RATIO)
    }
}
