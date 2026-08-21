// swift-tools-version:5.9
import PackageDescription

// The publish job rewrites this block — everything else in this file is ours. Note the
// tags are load-bearing: KMMBridge looks for them verbatim and fails the publish if
// they've drifted.
// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/code-payments/flipcash-shared-core-spm/releases/assets/524049263.zip"
let remoteKotlinChecksum = "b86241d770fa186e44eec4c9ff0ff092d54cf66329828485d243cb7b6cf588b5"
let packageName = "SharedCore"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v15)
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
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        ),
        .target(
            name: "SharedCoreKit",
            dependencies: [.target(name: packageName)]
        ),
        .testTarget(
            name: "SharedCoreKitTests",
            dependencies: ["SharedCoreKit"]
        ),
    ]
)
