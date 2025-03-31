package com.getcode.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.getcode.util.permissions.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissions @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionChecker {
    override fun isGranted(permission: String): Boolean {
        return check(permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun isDenied(permission: String): Boolean {
        return check(permission) == PackageManager.PERMISSION_DENIED
    }

    override fun check(permission: String): Int {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        )
    }
}