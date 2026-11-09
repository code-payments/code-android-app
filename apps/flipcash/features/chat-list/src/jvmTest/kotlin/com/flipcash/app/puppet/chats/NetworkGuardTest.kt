package com.flipcash.app.puppet.chats

import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.ClientCalls
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetworkGuardTest {
    private var previous: ProxySelector? = null

    @BeforeTest
    fun install() {
        previous = ProxySelector.getDefault()
        NetworkGuard.install()
        NetworkGuard.drain()
    }

    @AfterTest
    fun restore() {
        NetworkGuard.drain()
        ProxySelector.setDefault(previous)
    }

    @Test
    fun `blocks and records an OkHttp call`() {
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

        assertFailsWith<IOException> {
            client.newCall(Request.Builder().url("https://guard.test/ping").build()).execute()
        }

        val caught = NetworkGuard.drain()
        assertTrue(caught.any { "guard.test" in it }, "caught: $caught")
    }

    @Test
    fun `blocks and records a grpc-okhttp call`() {
        val channel = OkHttpChannelBuilder.forAddress("guard.test", 443).build()
        try {
            assertFailsWith<StatusRuntimeException> {
                ClientCalls.blockingUnaryCall(
                    channel.newCall(method, CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS)),
                    "ping",
                )
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        }

        val caught = NetworkGuard.drain()
        assertTrue(caught.any { "guard.test" in it }, "caught: $caught")
    }

    private val method: MethodDescriptor<String, String> = MethodDescriptor.newBuilder<String, String>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("guard.Test/Ping")
        .setRequestMarshaller(StringMarshaller)
        .setResponseMarshaller(StringMarshaller)
        .build()

    private object StringMarshaller : MethodDescriptor.Marshaller<String> {
        override fun stream(value: String): InputStream = value.byteInputStream()
        override fun parse(stream: InputStream): String = stream.readBytes().decodeToString()
    }
}
