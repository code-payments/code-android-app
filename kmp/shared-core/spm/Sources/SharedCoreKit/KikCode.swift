import Foundation
import SharedCore

/// Renders scannable codes from the shared Kotlin implementation.
public enum KikCode {

    /// The export size used when none is given; SVG scales losslessly, so this only sets the
    /// numbers in the document.
    public static var defaultDimension: Double { KikCodeSvg.shared.DEFAULT_DIMENSION }

    /// Renders `payload` as a standalone SVG document, byte-for-byte identical to Android's.
    ///
    /// - Parameters:
    ///   - background: the surface color the code sits on, or `nil` for a transparent document.
    ///     Codes are light-on-dark, so a transparent export is invisible on light surfaces.
    ///   - includeBadge: whether to embed the logo in the middle well.
    public static func svg(
        payload: Data,
        dimension: Double = KikCode.defaultDimension,
        foreground: String = "#FFFFFF",
        background: String? = nil,
        includeBadge: Bool = true
    ) -> String {
        KikCodeSvg.shared.render(
            payload: payload.kotlinByteArray,
            dimension: dimension,
            foreground: foreground,
            background: background,
            includeBadge: includeBadge
        )
    }
}
