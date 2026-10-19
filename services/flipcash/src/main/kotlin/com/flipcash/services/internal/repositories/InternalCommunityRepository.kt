package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.network.extensions.toMint
import com.flipcash.services.internal.network.services.CommunityService
import com.flipcash.services.models.Community
import com.flipcash.services.models.CommunityLeader
import com.flipcash.services.repository.CommunityRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.Mint
import com.getcode.utils.ErrorUtils

internal class InternalCommunityRepository(
    private val service: CommunityService,
    private val chatMapper: ChatMetadataMapper,
    private val userProfileMapper: UserProfileMapper,
) : CommunityRepository {
    override suspend fun getCommunity(owner: KeyPair, mint: Mint): Result<Community> {
        return service.getCommunity(owner, mint)
            .map { metadata ->
                Community(
                    mint = metadata.mint.toMint(),
                    name = metadata.name,
                    leader = if (metadata.hasLeader()) {
                        CommunityLeader(profile = userProfileMapper.map(metadata.leader.profile))
                    } else {
                        null
                    },
                    topHolderChats = metadata.topHolderChatsList.map { chatMapper.map(it) },
                )
            }
            .onFailure { ErrorUtils.handleError(it) }
    }
}
