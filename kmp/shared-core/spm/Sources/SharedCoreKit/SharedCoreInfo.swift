import SharedCore

/// Identifies the Kotlin framework this package was built from.
public enum SharedCoreInfo {

    /// The `:kmp:shared-core` version the linked XCFramework was published at.
    public static var version: String { SharedCore.SharedCoreBuild.shared.version }
}
