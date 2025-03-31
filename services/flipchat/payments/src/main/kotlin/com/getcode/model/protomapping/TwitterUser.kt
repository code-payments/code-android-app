package com.getcode.model.protomapping

import com.codeinc.gen.user.v1.CodeIdentityService as IdentityService
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.TwitterUser
import com.getcode.model.TwitterUser.VerificationStatus
import com.getcode.solana.keys.PublicKey

operator fun TwitterUser.Companion.invoke(proto: IdentityService.TwitterUser): TwitterUser? {
    val avatarUrl = proto.profilePicUrl

    val tipAddress = runCatching { PublicKey.fromByteString(proto.tipAddress.value) }.getOrNull() ?: return null

    return TwitterUser(
        username = proto.username,
        displayName = proto.name,
        imageUrl = avatarUrl,
        followerCount = proto.followerCount,
        tipAddress = tipAddress,
        verificationStatus = VerificationStatus.entries.getOrNull(proto.verifiedTypeValue) ?: VerificationStatus.unknown,
        costOfFriendship = Fiat(currency = CurrencyCode.USD, amount = 1.00),
        isFriend = false,
        chatId = emptyList()
    )
}