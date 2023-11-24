package com.kik.kikx.models

import java.io.Serializable

sealed class ScannableKikCode(open val colorIndex: Int) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    data class UsernameKikCode(val username: String, val nonce: Int, override val colorIndex: Int) : ScannableKikCode(colorIndex) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class GroupKikCode(val inviteCode: GroupInviteCode, override val colorIndex: Int) : ScannableKikCode(colorIndex) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class RemoteKikCode(val payloadId: ByteArray, override val colorIndex: Int) : ScannableKikCode(colorIndex) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
