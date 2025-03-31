package com.kik.kikx.kikcodes.implementation

import com.kik.kikx.kikcodes.KikCodeEncoder
import com.kik.scan.GroupKikCode
import com.kik.scan.KikCode
import com.kik.scan.UsernameKikCode

class KikCodeEncoderImpl : KikCodeEncoder {

    override fun encodeUsernameCode(username: String, nonce: Int, color: Int): ByteArray {
        return encode(UsernameKikCode(username, nonce, color))
    }

    override fun encodeGroupInviteKikCode(invitationCode: ByteArray, color: Int): ByteArray {
        return encode(GroupKikCode(invitationCode, color))
    }

    private fun encode(code: KikCode): ByteArray {
        return code.encode()
            ?: throw Exception("Unable to encode the Kik code. Was the library included and loaded properly?")
    }
}
