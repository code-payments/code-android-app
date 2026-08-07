package com.flipcash.shared

/**
 * Marker for the cross-platform shared core. Gives iOS a symbol to verify the
 * XCFramework is linked (`SharedCore.version`) and a home for the shared surface
 * as it grows across milestones.
 */
object SharedCore {
    const val version: String = "0.1.0"
}
