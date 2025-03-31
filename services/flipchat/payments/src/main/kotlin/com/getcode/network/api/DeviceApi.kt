package com.getcode.network.api

import com.codeinc.gen.common.v1.CodeModel as Model
import com.codeinc.gen.device.v1.DeviceGrpc
import com.codeinc.gen.device.v1.CodeDeviceService as DeviceService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.repository.sign
import com.getcode.network.repository.toSolanaAccount
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import javax.inject.Inject

class DeviceApi @Inject constructor(
    @PaymentsManagedChannel
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