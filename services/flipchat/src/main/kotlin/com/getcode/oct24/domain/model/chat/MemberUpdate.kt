package com.getcode.oct24.domain.model.chat

import com.getcode.oct24.data.Member

sealed interface MemberUpdate {
    data class Refresh(val members: List<Member>): MemberUpdate
}