package com.getcode.network.api

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.device.v1.DeviceGrpc
import com.codeinc.gen.device.v1.DeviceService
import com.getcode.annotations.CodeManagedChannel
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.repository.sign
import com.getcode.network.repository.toSolanaAccount
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class DeviceApi @Inject constructor(
    @CodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = DeviceGrpc.newStub(managedChannel).withWaitForReady()

    fun registerInstallation(owner: KeyPair, installationId: String) : Flow<DeviceService.RegisterLoggedInAccountsResponse> {
        val request = DeviceService.RegisterLoggedInAccountsRequest.newBuilder()
            .setAppInstall(Model.AppInstallId.newBuilder().setValue(installationId))
            .addOwners(owner.publicKeyBytes.toSolanaAccount())
            .apply {
                addAllSignatures(listOf(sign(owner)))
            }
            .build()

        return api::registerLoggedInAccounts
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchInstallationAccounts(installationId: String): Flow<DeviceService.GetLoggedInAccountsResponse> {
        val request = DeviceService.GetLoggedInAccountsRequest.newBuilder()
            .setAppInstall(Model.AppInstallId.newBuilder().setValue(installationId))
            .build()

        return api::getLoggedInAccounts
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}