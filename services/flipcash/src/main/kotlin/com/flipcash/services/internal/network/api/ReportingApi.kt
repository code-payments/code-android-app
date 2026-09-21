package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.reporting.v1.ReportingGrpcKt
import com.codeinc.flipcash.gen.reporting.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asMessageId
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.ReportTarget
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.utils.toByteString
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.codeinc.flipcash.gen.reporting.v1.ReportingService as RpcReportingService

@Singleton
internal class ReportingApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ReportingGrpcKt.ReportingCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun report(
        owner: KeyPair,
        target: ReportTarget,
        description: String,
    ): RpcReportingService.ReportResponse {
        val builder = RpcReportingService.ReportRequest.newBuilder()
            .setDescription(description)

        when (target) {
            is ReportTarget.User -> builder.setUserId(target.userId.asUserId())
            is ReportTarget.Chat -> builder.setChatId(target.chatId.asChatId())
            is ReportTarget.Message -> builder.setMessage(
                RpcReportingService.ReportRequest.ReportedMessage.newBuilder()
                    .setChatId(target.chatId.asChatId())
                    .setMessageId(target.messageId.asMessageId())
            )
            is ReportTarget.Blob -> builder.setBlobId(
                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                    .setValue(target.blobId.bytes.toByteString())
            )
        }

        // Auth last, always: `authenticate()` signs `buildPartial()`, so anything set after this
        // line is outside the signature. The target used to be set below it, which meant every
        // report was signed over its description alone.
        builder.apply { setAuth(authenticate(owner)) }

        val request = builder.build()
        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.report(request)
        }
    }
}
