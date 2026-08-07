// swift-tools-version:5.9
import PackageDescription

let packageName = "SharedCore"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            path: "./kmp/shared-core/build/XCFrameworks/debug/\(packageName).xcframework"
        )
        ,
    ]
)