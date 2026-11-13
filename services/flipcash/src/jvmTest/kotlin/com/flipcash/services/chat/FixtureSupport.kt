package com.flipcash.services.chat

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

// Shared by the fixture generators in this package.

internal fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
internal fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
internal fun JsonObject.list(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
internal fun List<Byte>.hex(): String = toByteArray().hex()
internal fun ByteArray.bs(): ByteString = ByteString.copyFrom(this)
internal fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun readJson(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

/**
 * Compares [captured] with the pinned [fixture], or rewrites the fixture when the build passes
 * `-Dfixture.update=true`.
 */
internal fun checkOrUpdateFixture(fixture: File, captured: JsonElement) {
    val pretty = Json { prettyPrint = true }
    if (System.getProperty("fixture.update") == "true") {
        fixture.parentFile.mkdirs()
        fixture.writeText(pretty.encodeToString(JsonElement.serializer(), captured) + "\n")
        return
    }
    check(fixture.exists()) { "$fixture is missing; run with -Dfixture.update=true to record it" }
    assertEquals(
        Json.parseToJsonElement(fixture.readText()),
        captured,
        "Android's output no longer matches $fixture; rerun with -Dfixture.update=true if the change is intended",
    )
}

/**
 * Records each message's serialized bytes and method name, and closes the call with UNAVAILABLE,
 * so nothing is sent.
 */
internal class CapturingChannel : ManagedChannel() {
    private val captured = mutableListOf<ByteArray>()
    val methods = mutableListOf<String>()

    fun takeOnly(): ByteArray = captured.single().also { captured.clear() }

    override fun <Req, Resp> newCall(method: MethodDescriptor<Req, Resp>, options: CallOptions): ClientCall<Req, Resp> =
        object : ClientCall<Req, Resp>() {
            private var listener: Listener<Resp>? = null
            override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                listener = responseListener
                methods += method.fullMethodName
            }
            override fun request(numMessages: Int) = Unit
            override fun cancel(message: String?, cause: Throwable?) = Unit
            override fun sendMessage(message: Req) {
                captured += method.streamRequest(message).readBytes()
            }
            override fun halfClose() {
                listener?.onClose(Status.UNAVAILABLE.withDescription("captured, not sent"), Metadata())
            }
        }

    override fun authority(): String = "capture.invalid"
    override fun shutdown(): ManagedChannel = this
    override fun isShutdown(): Boolean = false
    override fun isTerminated(): Boolean = false
    override fun shutdownNow(): ManagedChannel = this
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
