package xyz.flipchat.services.domain.model.chat

import xyz.flipchat.services.data.Member

sealed interface MemberUpdate {
    data class Refresh(val members: List<Member>): MemberUpdate
}