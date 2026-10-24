import Foundation
import SharedCore

/// The phone side of linking a desktop, run by the shared Kotlin: scan the desktop's QR code,
/// show the six-digit code, and send the seed once the user confirms both screens match.
///
/// The seed is passed to `confirm(entropyBase64:)` and to nothing else, so nothing leaves the
/// phone before the user has compared the codes. Call `close()` when the screen goes away.
@MainActor
public final class SharedDeviceLink {

    public enum State: Equatable, Sendable {
        case scanning
        case connecting
        case confirm(code: String)
        case sending
        case linked
        case failed(reason: String)
    }

    private let session: PhoneLinkSession

    /// `onState` is called on the main actor, starting with `.scanning`.
    public init(onState: @escaping @MainActor (State) -> Void) {
        session = PhoneLinkSession { state in
            let mapped = State(state)
            Task { @MainActor in onState(mapped) }
        }
    }

    /// Hands a scanned QR payload to the link. Returns false if it isn't a desktop link invite.
    @discardableResult
    public func scanned(_ text: String) -> Bool {
        session.scanned(text: text)
    }

    /// The user saw the same code on both screens: send the account's base64 entropy.
    public func confirm(entropyBase64: String) {
        session.confirm(entropyB64: entropyBase64)
    }

    /// Drops the pending link, if any, and goes back to scanning.
    public func cancel() {
        session.cancel()
    }

    public func close() {
        session.close()
    }
}

extension SharedDeviceLink.State {

    init(_ state: PhoneLinkControllerState) {
        switch state {
        case let confirm as PhoneLinkControllerStateConfirm: self = .confirm(code: confirm.code)
        case let failed as PhoneLinkControllerStateFailed: self = .failed(reason: failed.reason)
        case is PhoneLinkControllerStateConnecting: self = .connecting
        case is PhoneLinkControllerStateSending: self = .sending
        case is PhoneLinkControllerStateLinked: self = .linked
        default: self = .scanning
        }
    }
}
