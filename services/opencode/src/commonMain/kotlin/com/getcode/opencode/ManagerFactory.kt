package com.getcode.opencode

import com.getcode.opencode.internal.manager.VerifiedProtoManager

object ManagerFactory {
    private val verifiedStateManager: VerifiedProtoManager by lazy { VerifiedProtoManager() }

    fun createVerifiedStateManager() = verifiedStateManager
}