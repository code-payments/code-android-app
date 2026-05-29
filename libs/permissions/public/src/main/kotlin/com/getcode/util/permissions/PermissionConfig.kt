package com.getcode.util.permissions

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Describes a runtime permission and its associated UI metadata.
 *
 * Use [PermissionConfigs] for pre-built configs, or construct directly
 * for one-off permissions.
 *
 * @property permission The Android permission string (e.g. [Manifest.permission.CAMERA]).
 * @property minSdk The minimum SDK level at which this permission requires a runtime request.
 *   Below this level, the permission is implicitly granted — the system dialog is never shown.
 *   Defaults to [Build.VERSION_CODES.BASE] (always requires runtime request).
 * @property maxSdk The maximum SDK level at which this permission requires a runtime request.
 *   Above this level, the permission is implicitly granted — the system dialog is never shown.
 *   Useful for permissions superseded by newer APIs (e.g. [Manifest.permission.WRITE_EXTERNAL_STORAGE]
 *   is unnecessary on API 29+ where MediaStore / SAF handle storage access).
 *   Defaults to [Int.MAX_VALUE] (no upper bound).
 */
data class PermissionConfig(
    val permission: String,
    val minSdk: Int = Build.VERSION_CODES.BASE,
    val maxSdk: Int = Int.MAX_VALUE,
) {
    /**
     * True if the current device SDK requires a runtime permission request.
     * False below [minSdk], in which case the permission is auto-granted.
     */
    val requiresRuntimeRequest: Boolean
        get() = Build.VERSION.SDK_INT >= minSdk
}

/**
 * Pre-built [PermissionConfig] factories for common permissions.
 *
 * Each factory is a [Composable] to allow string resource resolution via [androidx.compose.ui.platform.LocalContext].
 * Results are [remember]ed to avoid unnecessary allocations on recomposition.
 */
object PermissionConfigs {
    /**
     * Config for [Manifest.permission.POST_NOTIFICATIONS].
     * Only requires a runtime request on API 33+ (TIRAMISU). Auto-granted below that.
     */
    @Composable
    fun notifications(): PermissionConfig {
        return remember {
            PermissionConfig(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                minSdk = Build.VERSION_CODES.TIRAMISU,
            )
        }
    }

    /**
     * Config for [Manifest.permission.CAMERA].
     * Requires a runtime request on all supported API levels.
     */
    @Composable
    fun camera(): PermissionConfig {
        return remember {
            PermissionConfig(
                permission = Manifest.permission.CAMERA,
            )
        }
    }

    /**
     * Config for [Manifest.permission.READ_CONTACTS].
     * Requires a runtime request on all supported API levels.
     */
    @Composable
    fun contacts(): PermissionConfig {
        return remember {
            PermissionConfig(
                permission = Manifest.permission.READ_CONTACTS,
            )
        }
    }

    /**
     * Config for [Manifest.permission.WRITE_EXTERNAL_STORAGE].
     * Requires a runtime request on all supported API levels.
     */
    @Composable
    fun externalStorage(): PermissionConfig {
        return remember {
            PermissionConfig(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                minSdk = Build.VERSION_CODES.BASE,
                maxSdk = Build.VERSION_CODES.P,
            )
        }
    }
}