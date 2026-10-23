package com.getcode.navigation

import androidx.navigation3.runtime.NavKey

@JvmInline
value class NavContentKey(val contentKey: String)

// Helper currently aligned with NavKey.defaultContentKey
fun NavKey.contentKey() = NavContentKey(this.toString())
