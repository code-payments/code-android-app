import Foundation
import SharedCore

/// The Kotlin vocabulary, under a local name. Deliberately a typealias and not a Swift enum:
/// the tokens are the wire format, and there must be exactly one place they are written down.
public typealias ReportReason = SharedCore.ReportReason

/// Assembles the `description` field of a `ReportRequest`, in the shared Kotlin.
public enum ReportDescription {

    /// The cap the composer enforces as you type.
    public static let maxDetailsLength = Int(SharedCore.ReportDescription.shared.MAX_DETAILS_LENGTH)

    /// The `description` field of a `ReportRequest`: reason token, then the person's words.
    public static func build(reason: ReportReason, details: String?) -> String {
        SharedCore.ReportDescription.shared.build(reason: reason, details: details)
    }
}

public extension ReportReason {
    /// The order the reasons are offered in. This *is* app UI, which is why it lives on the
    /// Swift side -- but it must stay exhaustive, and `ReportReasonTests` checks that it is.
    ///
    /// The case names are the ones Kotlin/Native generates, which lowercases the whole word
    /// rather than preserving the Kotlin hump: `scamorfraud`, not `scamOrFraud`.
    static var displayOrder: [ReportReason] {
        [.spam, .scamorfraud, .harassment, .sexualcontent, .violence, .other]
    }
}
