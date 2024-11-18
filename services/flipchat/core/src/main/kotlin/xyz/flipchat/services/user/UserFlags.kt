package xyz.flipchat.services.user

import com.getcode.model.Kin
import com.getcode.solana.keys.PublicKey

data class UserFlags(
    val isStaff: Boolean,
    val createCost: Kin,
    val feeDestination: PublicKey
)
