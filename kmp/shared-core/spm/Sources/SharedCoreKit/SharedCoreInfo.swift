import SharedCore

/// Identifies the Kotlin framework this package was built from.
public enum SharedCoreInfo {

    /// The `:kmp:shared-core` version the linked XCFramework was published at.
    // Unqualified on purpose: inside this module the name `SharedCore` resolves to the
    // Kotlin object, not the framework it lives in.
    public static var version: String { SharedCore.shared.version }
}
