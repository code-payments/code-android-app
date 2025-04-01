package com.getcode.util.permissions

interface PermissionChecker {
    fun isGranted(permission: String): Boolean
    fun isDenied(permission: String): Boolean
    fun check(permission: String): Int
}