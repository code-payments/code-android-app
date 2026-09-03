// swift-tools-version:5.9
import PackageDescription
import Foundation

// The publish job rewrites this block — everything else in this file is ours. Note the
// tags are load-bearing: KMMBridge looks for them verbatim and fails the publish if
// they've drifted.
// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/code-payments/flipcash-shared-core-spm/releases/assets/524049263.zip"
let remoteKotlinChecksum = "b86241d770fa186e44eec4c9ff0ff092d54cf66329828485d243cb7b6cf588b5"
let packageName = "SharedCore"
// END KMMBRIDGE BLOCK

// FLIPCASH_SHARED_CORE_LOCAL points at a code-android-app checkout and swaps the published
// XCFramework for one assembled from that checkout's Kotlin, so a Kotlin change can be tried
// from an iOS build without cutting a release and moving a tag:
//
//   ./gradlew :kmp:shared-core:assembleSharedCoreReleaseXCFramework
//
// This manifest is what gets published, so the override also works from the tagged copy the
// iOS app resolves — nothing on the iOS side has to change. That copy sits in DerivedData, and
// SwiftPM only accepts a binary target path relative to the package root, so the absolute path
// the variable gives us is rewritten as a relative one from wherever the manifest was checked
// out to.
//
// The publish workflow never sets the variable, so CI always resolves the binary target
// through the URL and checksum above.
func relativePath(from base: String, to target: String) -> String {
    let from = (base as NSString).resolvingSymlinksInPath.split(separator: "/").map(String.init)
    let to = (target as NSString).resolvingSymlinksInPath.split(separator: "/").map(String.init)
    var shared = 0
    while shared < from.count, shared < to.count, from[shared] == to[shared] { shared += 1 }
    return (Array(repeating: "..", count: from.count - shared) + to[shared...]).joined(separator: "/")
}

let sharedCoreLocalRoot = ProcessInfo.processInfo.environment["FLIPCASH_SHARED_CORE_LOCAL"]
    .map { ($0 as NSString).expandingTildeInPath }
    .flatMap { $0.isEmpty ? nil : $0 }

let binaryTarget: Target = sharedCoreLocalRoot.map {
    .binaryTarget(
        name: packageName,
        path: relativePath(
            from: Context.packageDirectory,
            to: "\($0)/kmp/shared-core/build/XCFrameworks/release/\(packageName).xcframework"
        )
    )
} ?? .binaryTarget(
    name: packageName,
    url: remoteKotlinUrl,
    checksum: remoteKotlinChecksum
)

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v15),
        .macOS(.v14),
    ],
    products: [
        // The only product on purpose. Callers get Swift types; the Kotlin framework's
        // own surface — `KotlinByteArray`, `.shared` singletons, no default arguments —
        // stays behind this target.
        .library(
            name: "SharedCoreKit",
            targets: ["SharedCoreKit"]
        ),
    ],
    targets: [
        binaryTarget,
        .target(
            name: "SharedCoreKit",
            dependencies: [.target(name: packageName)]
        ),
        .testTarget(
            name: "SharedCoreKitTests",
            dependencies: ["SharedCoreKit"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
