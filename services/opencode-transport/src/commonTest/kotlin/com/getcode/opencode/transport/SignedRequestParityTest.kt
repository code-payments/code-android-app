package com.getcode.opencode.transport

import com.getcode.ed25519kmp.Ed25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Wire builders must produce the bytes Android sends. `signed_requests.json` is captured from
 * `:services:opencode`'s real `AccountApi` (protobuf-javalite) by its `SignedRequestFixtureTest` on
 * the JVM; this runs on the JVM, Node and the iOS simulator against the same file.
 *
 * Ed25519 signatures are deterministic, so equal unsigned bytes and keys give equal signatures.
 */
class SignedRequestParityTest {

    private val fixture = Json.parseToJsonElement(readTestResource("signed_requests.json")).jsonObject
    private val owner = Ed25519.createKeyPair(fixture.hex("ownerSeed"))
    private val requestingOwner = Ed25519.createKeyPair(fixture.hex("requestingOwnerSeed"))

    @Test
    fun isOcpAccountMatchesAndroid() {
        val pinned = fixture.section("isOcpAccount")
        val request = SignedRequests.isOcpAccount(owner)

        assertEquals(pinned.text("unsigned"), request.copy(signature = null).encodeCanonical().hex())
        assertEquals(pinned.text("signature"), request.signature!!.value_.hex())
        assertEquals(pinned.text("request"), request.encodeCanonical().hex())
    }

    @Test
    fun getTokenAccountInfosWithMintFilterMatchesAndroid() {
        val pinned = fixture.section("getTokenAccountInfosByMint")
        val request = SignedRequests.getTokenAccountInfos(owner, requestingOwner, fixture.hex("mint"))

        val unsigned = request.copy(signature = null, requesting_owner_signature = null)
        assertEquals(pinned.text("unsigned"), unsigned.encodeCanonical().hex())
        assertEquals(pinned.text("ownerSignature"), request.signature!!.value_.hex())
        assertEquals(pinned.text("requestingOwnerSignature"), request.requesting_owner_signature!!.value_.hex())
        assertEquals(pinned.text("request"), request.encodeCanonical().hex())
    }
}

private fun JsonObject.section(name: String): JsonObject = getValue(name).jsonObject
private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun JsonObject.hex(name: String): ByteArray =
    text(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
private fun okio.ByteString.hex(): String = hex()
