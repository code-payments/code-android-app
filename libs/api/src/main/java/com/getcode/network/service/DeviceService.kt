package com.getcode.network.service

import com.codeinc.gen.device.v1.DeviceService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.api.DeviceApi
import com.getcode.network.core.NetworkOracle
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class DeviceService @Inject constructor(
    private val api: DeviceApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun registerInstallation(owner: KeyPair, installationId: String): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.registerInstallation(owner, installationId))
                .map { response ->
                    when (response.result) {
                        DeviceService.RegisterLoggedInAccountsResponse.Result.OK -> Result.success(Unit)

                        DeviceService.RegisterLoggedInAccountsResponse.Result.INVALID_OWNER -> {
                            val error = Throwable("Error: Invalid owner.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        DeviceService.RegisterLoggedInAccountsResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun fetchInstallations(installationId: String): Result<List<PublicKey>> {
        return try {
            networkOracle.managedRequest(api.fetchInstallationAccounts(installationId))
                .map { response ->
                    when (response.result) {
                        DeviceService.GetLoggedInAccountsResponse.Result.OK -> {
                            Result.success(response.ownersList.map {
                                PublicKey(
                                    it.value.toByteArray().toList()
                                )
                            })
                        }
                        DeviceService.GetLoggedInAccountsResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}