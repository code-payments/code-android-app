package com.kik.kikx.models

import java.io.Serializable

data class GroupInviteCode(
    val code: Id // base64-encoded representation of the invite code (20 random bytes). Should not contain any padding (=)
){
    data class Id(val value: ByteArray) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 0L
        }
    }
}
