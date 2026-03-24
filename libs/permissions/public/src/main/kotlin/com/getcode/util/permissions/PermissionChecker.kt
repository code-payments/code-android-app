package com.getcode.util.permissions

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Abstraction over Android's permission checking APIs.
 *
 * Kept internal — callers interact with the permission system exclusively
 * through [rememberPermission] and [PermissionHandle]. Exposed only via
 * [LocalPermissionChecker] for injection, and [ProvideTestPermissions] for testing.
 */
interface PermissionChecker {
    /** Returns true if [permission] is currently granted. */
    fun isGranted(permission: String): Boolean
    /**
     * Returns true if the system recommends showing a rationale before re-requesting [permission].
     *
     * Note: This is unreliable for cross-session inference — some OEMs never return true.
     * Only use this inside a permission launcher result callback, never for persisted state.
     */
    fun shouldShowRationale(permission: String): Boolean
}

/**
 * Default implementation used when no [PermissionChecker] has been provided
 * via [LocalPermissionChecker]. Treats all permissions as denied.
 */
private object DefaultPermissionChecker : PermissionChecker {
    override fun isGranted(permission: String) = false
    override fun shouldShowRationale(permission: String) = false
}

/**
 * Test implementation that treats a fixed set of permissions as granted.
 * All others are treated as denied with no rationale.
 */
private class FakePermissionChecker(
    private val granted: Set<String>
) : PermissionChecker {
    override fun isGranted(permission: String) = permission in granted
    override fun shouldShowRationale(permission: String) = false
}

/**
 * [androidx.compose.runtime.CompositionLocal] providing the active [PermissionChecker].
 *
 * Internal — replaced in tests via [ProvideTestPermissions].
 * In production, provide [AndroidPermissionChecker] via your DI-aware
 * composition root.
 */
internal val LocalPermissionChecker: ProvidableCompositionLocal<PermissionChecker> =
    staticCompositionLocalOf { DefaultPermissionChecker }

/**
 * Provides a [FakePermissionChecker] for use in tests and Compose previews.
 *
 * @param granted The set of permission strings to treat as granted.
 *   Defaults to empty — all permissions denied.
 *
 * Usage:
 * ```
 * ProvideTestPermissions(granted = setOf(Manifest.permission.CAMERA)) {
 *     CameraScreen()
 * }
 * ```
 */
@VisibleForTesting
@Composable
fun ProvideTestPermissions(
    granted: Set<String> = emptySet(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPermissionChecker provides FakePermissionChecker(granted),
        content = content,
    )
}