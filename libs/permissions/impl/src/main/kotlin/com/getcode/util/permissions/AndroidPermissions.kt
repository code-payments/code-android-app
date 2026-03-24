package com.getcode.util.permissions

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import javax.inject.Inject

/**
 * Production implementation backed by [ActivityCompat] and [ContextCompat].
 * Requires an [Activity] reference for [shouldShowRationale] — inject via Hilt
 * using an activity-scoped component.
 */
class AndroidPermissionChecker @Inject constructor(
    private val activity: Activity
) : PermissionChecker {
    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    override fun shouldShowRationale(permission: String): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) && !isGranted(permission)
}