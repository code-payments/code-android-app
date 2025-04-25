package com.flipcash.app.core.credentials

import com.getcode.opencode.model.core.ID
import com.getcode.utils.base58
import com.getcode.vendor.Base58

data class AccountMetadata(
    internal val _accountId: String,
    val entropy: String,
    val isUnregistered: Boolean,
) {

    val id: ID
        get() = Base58.decode(_accountId).toList()

    companion object {
        fun createFromId(id: ID, entropy: String, isUnregistered: Boolean): AccountMetadata {
            return AccountMetadata(id.base58, entropy, isUnregistered)
        }
    }
}