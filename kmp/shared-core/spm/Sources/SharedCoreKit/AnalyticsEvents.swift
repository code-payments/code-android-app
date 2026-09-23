import SharedCore

/// One event, built by the shared Kotlin. Named `TrackedEvent` rather than re-exporting
/// `AnalyticsEvent` because the iOS app already declares `protocol AnalyticsEvent`.
public typealias TrackedEvent = SharedCore.AnalyticsEvent

// The value vocabulary. Deliberately typealiases and not Swift enums, as with `ReportReason`:
// the wire strings are written down once, in
// `libs/analytics-events/events.toml`. Kotlin's `Button`, `State` and
// `Amount` are renamed here so they don't read as SwiftUI's `Button` and `@State`, or as
// one of the app's money types.
public typealias ChatType = SharedCore.ChatType
public typealias EventState = SharedCore.State
public typealias AddMoneySource = SharedCore.AddMoneySource
public typealias AddMoneyMethod = SharedCore.AddMoneyMethod
public typealias DisplayNameSource = SharedCore.DisplayNameSource
public typealias TokenInfoSource = SharedCore.TokenInfoSource
public typealias PurchaseMethod = SharedCore.PurchaseMethod
public typealias WalletProvider = SharedCore.WalletProvider
public typealias CashLinkChoice = SharedCore.CashLinkChoice
public typealias OnrampStep = SharedCore.OnrampStep
public typealias AnalyticsButton = SharedCore.Button
public typealias AnalyticsAmount = SharedCore.Amount
public typealias PeopleCounter = SharedCore.PeopleCounter

// The builders. Each is a Kotlin `object`, so calls go through `.shared`.
public typealias ChatEvents = SharedCore.ChatEvents
public typealias TransferEvents = SharedCore.TransferEvents
public typealias AddMoneyEvents = SharedCore.AddMoneyEvents
public typealias OnrampEvents = SharedCore.OnrampEvents
public typealias WalletEvents = SharedCore.WalletEvents
public typealias TokenInfoEvents = SharedCore.TokenInfoEvents
public typealias SwapEvents = SharedCore.SwapEvents
public typealias ScanEvents = SharedCore.ScanEvents
public typealias DeeplinkEvents = SharedCore.DeeplinkEvents
public typealias DisplayNameEvents = SharedCore.DisplayNameEvents
public typealias ErrorModalEvents = SharedCore.ErrorModalEvents
public typealias AccountEvents = SharedCore.AccountEvents
public typealias ButtonEvents = SharedCore.ButtonEvents

/// A property value as the iOS sender needs it. Mixpanel is not a dependency of this
/// package, so the app maps these to `MixpanelType` itself.
public enum AnalyticsScalar: Equatable, Sendable {
    case text(String)
    case number(Double)
    case flag(Bool)
}

public extension TrackedEvent {
    /// Kotlin's sealed `PropertyValue` bridges as a protocol with no exhaustiveness, so a
    /// fourth case crashes here, in the facade's own tests, rather than sending nothing.
    var scalarProperties: [String: AnalyticsScalar] {
        properties.mapValues { value in
            switch value {
            case let v as PropertyValueText: .text(v.value)
            case let v as PropertyValueNumber: .number(v.value)
            case let v as PropertyValueFlag: .flag(v.value)
            default: preconditionFailure("PropertyValue gained a case the facade does not map: \(value)")
            }
        }
    }
}
