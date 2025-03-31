package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.profile.v1.Model
import com.codeinc.flipchat.gen.profile.v1.ProfileService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.model.profile.SocialAccountLinkRequest
import com.getcode.services.model.profile.SocialAccountUnlinkRequest
import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.internal.network.api.ProfileApi
import com.getcode.utils.FlipchatServerError
import javax.inject.Inject

internal class ProfileService @Inject constructor(
    private val api: ProfileApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getProfile(userId: ID): Result<Model.UserProfile> {
        return try {
            networkOracle.managedRequest(api.getProfile(userId))
                .map {
                    when (it.result) {
                        ProfileService.GetProfileResponse.Result.OK -> Result.success(it.userProfile)
                        ProfileService.GetProfileResponse.Result.NOT_FOUND -> {
                            val error = GetProfileError.NotFound()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        ProfileService.GetProfileResponse.Result.UNRECOGNIZED -> {
                            val error = GetProfileError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = GetProfileError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetProfileError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun setDisplayName(owner: KeyPair, displayName: String): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setDisplayName(owner, displayName))
                .map {
                    when (it.result) {
                        ProfileService.SetDisplayNameResponse.Result.OK -> Result.success(Unit)
                        ProfileService.SetDisplayNameResponse.Result.INVALID_DISPLAY_NAME -> {
                            val error = SetUserDisplayNameError.InvalidDisplayName()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        ProfileService.SetDisplayNameResponse.Result.UNRECOGNIZED -> {
                            val error = SetUserDisplayNameError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = SetUserDisplayNameError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = SetUserDisplayNameError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun linkSocialAccount(
        owner: KeyPair,
        request: SocialAccountLinkRequest
    ): Result<Model.SocialProfile> {
        return networkOracle.managedApiRequest(
            call = { api.linkSocialAccount(owner, request) },
            handleResponse = { response ->
                when (response.result) {
                    ProfileService.LinkSocialAccountResponse.Result.OK -> Result.success(response.socialProfile)
                    ProfileService.LinkSocialAccountResponse.Result.INVALID_LINKING_TOKEN -> Result.failure(LinkSocialAccountError.InvalidLinkingToken())
                    ProfileService.LinkSocialAccountResponse.Result.EXISTING_LINK -> Result.failure(LinkSocialAccountError.ExistingLink())
                    ProfileService.LinkSocialAccountResponse.Result.DENIED -> Result.failure(LinkSocialAccountError.Denied())
                    ProfileService.LinkSocialAccountResponse.Result.UNRECOGNIZED -> Result.failure(LinkSocialAccountError.Unrecognized())
                    else -> {
                        Result.failure(LinkSocialAccountError.Other())
                    }
                }
            },
            onOtherError = { cause ->
                Result.failure(LinkSocialAccountError.Other(cause = cause))
            }
        )
    }

    suspend fun unlinkSocialAccount(
        owner: KeyPair,
        request: SocialAccountUnlinkRequest,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.unlinkSocialAccount(owner, request) },
            handleResponse = { response ->
                when (response.result) {
                    ProfileService.UnlinkSocialAccountResponse.Result.OK -> Result.success(Unit)
                    ProfileService.UnlinkSocialAccountResponse.Result.DENIED -> Result.failure(UnlinkSocialAccountError.Denied())
                    ProfileService.UnlinkSocialAccountResponse.Result.UNRECOGNIZED -> Result.failure(UnlinkSocialAccountError.Unrecognized())
                    else -> {
                        Result.failure(UnlinkSocialAccountError.Other())
                    }
                }
            },
            onOtherError = { cause ->
                Result.failure(UnlinkSocialAccountError.Other(cause = cause))
            }
        )
    }
}

sealed class GetProfileError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class NotFound : GetProfileError()
    class Unrecognized : GetProfileError()
    data class Other(override val cause: Throwable? = null) : GetProfileError(cause = cause)
}

sealed class SetUserDisplayNameError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class InvalidDisplayName : SetUserDisplayNameError()
    class Unrecognized : SetUserDisplayNameError()
    data class Other(override val cause: Throwable? = null) : SetUserDisplayNameError(cause = cause)
}

sealed class LinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class InvalidLinkingToken : LinkSocialAccountError()
    class ExistingLink : LinkSocialAccountError()
    class Denied : LinkSocialAccountError()
    class Unrecognized : LinkSocialAccountError()
    data class Other(override val cause: Throwable? = null) : LinkSocialAccountError(cause = cause)
}

sealed class UnlinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Denied : UnlinkSocialAccountError()
    class Unrecognized : UnlinkSocialAccountError()
    data class Other(override val cause: Throwable? = null) : UnlinkSocialAccountError(cause = cause)
}