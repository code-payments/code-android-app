package com.flipcash.services.models

import com.flipcash.services.models.chat.ChatMetadata
import com.getcode.solana.keys.Mint

/**
 * A community built around a Flipcash-launched currency.
 *
 * @param mint The currency the community is built around
 * @param name The community's display name
 * @param leader The community's leader, if one has been established
 * @param topHolderChats Chats among the currency's top holders, as returned by the server
 */
data class Community(
    val mint: Mint,
    val name: String,
    val leader: CommunityLeader?,
    val topHolderChats: List<ChatMetadata>,
)

data class CommunityLeader(
    val profile: UserProfile,
)
