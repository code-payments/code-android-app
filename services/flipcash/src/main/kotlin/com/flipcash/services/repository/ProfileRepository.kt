package com.flipcash.services.repository

import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.MediaItem
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID

interface ProfileRepository {
    suspend fun getProfile(userId: ID, owner: Ed25519.KeyPair): Result<UserProfile>
    suspend fun setDisplayName(displayName: String, owner: Ed25519.KeyPair): Result<Unit>
    suspend fun setProfilePicture(blobId: BlobId, owner: Ed25519.KeyPair): Result<MediaItem>
    suspend fun linkSocialAccount(request: SocialAccountLinkRequest, owner: Ed25519.KeyPair): Result<SocialAccount>
    suspend fun unlinkSocialAccount(request: SocialAccountUnlinkRequest, owner: Ed25519.KeyPair): Result<Unit>
}