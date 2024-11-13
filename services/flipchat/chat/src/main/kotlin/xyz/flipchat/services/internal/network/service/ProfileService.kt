package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.profile.v1.Model
import com.codeinc.flipchat.gen.profile.v1.ProfileService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.internal.network.api.ProfileApi
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
                            val error = GetProfileError.NotFound
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        ProfileService.GetProfileResponse.Result.UNRECOGNIZED -> {
                            val error = GetProfileError.Unrecognized
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
                            val error = SetDisplayNameError.InvalidDisplayName
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        ProfileService.SetDisplayNameResponse.Result.UNRECOGNIZED -> {
                            val error = SetDisplayNameError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = SetDisplayNameError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = SetDisplayNameError.Other(cause = e)
            Result.failure(error)
        }
    }

    internal sealed class GetProfileError : Throwable() {
        data object NotFound : GetProfileError()
        data object Unrecognized : GetProfileError()
        data class Other(override val cause: Throwable? = null) : GetProfileError()
    }

    internal sealed class SetDisplayNameError : Throwable() {
        data object InvalidDisplayName : SetDisplayNameError()
        data object Unrecognized : SetDisplayNameError()
        data class Other(override val cause: Throwable? = null) : SetDisplayNameError()
    }
}