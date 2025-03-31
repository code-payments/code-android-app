package com.kik.kikx.kikcodes


interface KikCodeEncoder {
    fun encodeUsernameCode(username: String, nonce: Int, color: Int): ByteArray
    fun encodeGroupInviteKikCode(invitationCode: ByteArray, color: Int): ByteArray
}
