package com.flipcash.services.internal.network.api

import androidx.lifecycle.ProcessLifecycleOwner
import com.codeinc.flipcash.gen.common.v1.Common
import com.getcode.ed25519.Ed25519
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.okhttp.OkHttpChannelBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.utils.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BlobStorageApiDeadlineTest {

    // Nothing listens on port 1, so the channel never becomes ready. With wait-for-ready and no
    // deadline, a call there waits for as long as the device stays offline.
    private val channel: ManagedChannel = OkHttpChannelBuilder.forAddress("127.0.0.1", 1)
        .usePlaintext()
        .build()

    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)

    @Before
    fun setUp() {
        // GrpcApi registers with the process lifecycle on construction, which needs a main looper.
        mockkObject(ProcessLifecycleOwner.Companion)
        every { ProcessLifecycleOwner.get() } returns mockk(relaxed = true)
        // Signing needs the native Ed25519 library; the request only has to get as far as the wire.
        mockkStatic(AUTHENTICATE)
        every { any<com.google.protobuf.GeneratedMessageLite.Builder<*, *>>().authenticate(any()) } returns signedAuth
    }

    @After
    fun tearDown() {
        channel.shutdownNow()
        unmockkObject(ProcessLifecycleOwner.Companion)
        unmockkStatic(AUTHENTICATE)
    }

    @Test
    fun `a call that cannot reach the server fails at the deadline`() = runBlocking {
        val api = BlobStorageApi(channel, unaryDeadline = 200.milliseconds)

        val error = assertFailsWith<StatusException> {
            withTimeout(5.seconds) { api.getUploadPolicy(owner) }
        }

        assertEquals(Status.Code.DEADLINE_EXCEEDED, error.status.code)
    }

    private companion object {
        const val AUTHENTICATE = "com.flipcash.services.internal.network.extensions.AuthenticateMessageKt"

        val signedAuth: Common.Auth = Common.Auth.newBuilder()
            .setKeyPair(
                Common.Auth.KeyPair.newBuilder()
                    .setPubKey(Common.PublicKey.newBuilder().setValue(ByteArray(32) { 1 }.toByteString()))
                    .setSignature(Common.Signature.newBuilder().setValue(ByteArray(64) { 2 }.toByteString())),
            )
            .build()
    }
}
