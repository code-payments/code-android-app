package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.SocialAccountMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.network.services.ProfileService
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.models.SetDisplayNameError
import com.flipcash.services.models.SetUsernameError
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.financial.Fiat
import com.getcode.utils.ErrorUtils

internal class InternalProfileRepository(
    private val service: ProfileService,
    private val userProfileMapper: UserProfileMapper,
    private val socialAccountMapper: SocialAccountMapper,
): ProfileRepository {
    override suspend fun getProfile(
        identifier: ProfileIdentifier,
        owner: Ed25519.KeyPair,
    ): Result<UserProfile> {
        return service.getProfile(identifier, owner)
            .map { userProfileMapper.map(it) }
            .onFailure {
                if (it !is GetUserProfileError.NotFound) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun setDisplayName(
        displayName: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return service.setDisplayName(displayName, owner)
            .onFailure {
                // The rejections below are the server answering a user's choice of
                // display name, not a fault worth reporting.
                val expected = it is SetDisplayNameError.InvalidDisplayName ||
                        it is SetDisplayNameError.FailedModerated
                if (!expected) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun setUsername(
        username: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return service.setUsername(username, owner)
            .onFailure {
                // The rejections below are the server answering a user's choice of
                // username, not a fault worth reporting.
                val expected = it is SetUsernameError.InvalidUsername ||
                        it is SetUsernameError.AlreadyTaken ||
                        it is SetUsernameError.ReservedWord ||
                        it is SetUsernameError.FailedModerated ||
                        it is SetUsernameError.InsufficientBalance
                if (!expected) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun setProfilePicture(
        blobId: BlobId,
        owner: Ed25519.KeyPair
    ): Result<MediaItem> {
        return service.setProfilePicture(blobId, owner)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun updateTipCard(
        owner: Ed25519.KeyPair,
        hexColor: String,
    ): Result<Unit> {
        return service.updateTipCard(owner, hexColor)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun setMinDmChatInitFee(
        owner: Ed25519.KeyPair,
        fee: Fiat,
    ): Result<Unit> {
        return service.setMinDmChatInitFee(owner, fee)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun linkSocialAccount(
        request: SocialAccountLinkRequest,
        owner: Ed25519.KeyPair
    ): Result<SocialAccount> {
        return service.linkSocialAccount(request, owner)
            .map { socialAccountMapper.map(it) }
            .fold(
                onSuccess = { account ->
                    if (account == null) {
                        Result.failure(Exception("Mapping of social account resulted in a null value"))
                    } else {
                        Result.success(account)
                    }
                },
                onFailure = { Result.failure(it) }
            ).onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun unlinkSocialAccount(
        request: SocialAccountUnlinkRequest,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return service.unlinkSocialAccount(request, owner)
            .onFailure { ErrorUtils.handleError(it) }
    }
}