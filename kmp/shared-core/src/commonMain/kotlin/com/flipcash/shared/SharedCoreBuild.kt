package com.flipcash.shared

/**
 * Marker for the cross-platform shared core. Gives iOS a symbol to verify the
 * XCFramework is linked (`SharedCoreBuild.version`).
 *
 * Deliberately not named `SharedCore`: Kotlin/Native exports it into the framework of the same
 * name, and a type called `SharedCore` shadows the module called `SharedCore` on the Swift side,
 * so `SharedCore.Base58` stops resolving and no facade can qualify a Kotlin type it collides with.
 */
object SharedCoreBuild {
    const val version: String = "0.1.0"
}
