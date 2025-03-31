package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.utils.base58
import com.getcode.utils.bytes
import com.getcode.vendor.Base58
import java.util.UUID
import javax.inject.Inject

class IdentityManager @Inject constructor() {
    fun generateVerificationTweet(accountName: String): String? {
        val authority = SessionManager.getOrganizer()?.tray?.owner?.getCluster()?.authority
        val tipAddress = SessionManager.getOrganizer()?.primaryVault
            ?.let { Base58.encode(it.byteArray) }

        if (tipAddress != null && authority != null) {
            val nonce = UUID.randomUUID()
            val signature = authority.keyPair.sign(nonce.bytes.toByteArray())
            val verificationMessage = listOf(
                accountName,
                tipAddress,
                Base58.encode(nonce.bytes.toByteArray()),
                signature.base58
            ).joinToString(":")

            return verificationMessage
        }

        return null
    }
}