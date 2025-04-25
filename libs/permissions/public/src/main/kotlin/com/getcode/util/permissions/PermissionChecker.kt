package com.getcode.util.permissions

import android.content.pm.PackageManager
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

interface PermissionChecker {
    fun isGranted(permission: String): Boolean
    fun isDenied(permission: String): Boolean
    fun check(permission: String): Int
}

val LocalPermissionChecker: ProvidableCompositionLocal<PermissionChecker> = staticCompositionLocalOf { Denied }

private object Denied : PermissionChecker {
    override fun isGranted(permission: String): Boolean = false
    override fun isDenied(permission: String): Boolean = false
    override fun check(permission: String): Int = PackageManager.PERMISSION_DENIED

}