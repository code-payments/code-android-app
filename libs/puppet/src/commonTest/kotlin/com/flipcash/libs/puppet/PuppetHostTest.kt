package com.flipcash.libs.puppet

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PuppetHostTest {

    @Serializable
    enum class Size { Small, Large }

    @Serializable
    @SerialName("Resize")
    data class Resize(val size: Size, val note: String? = null)

    @Serializable
    @SerialName("Reset")
    data object Reset

    private var size = Size.Small

    private val host = PuppetHost(
        root = {
            PuppetNode(
                name = "Box",
                state = { JsonObject(mapOf("size" to JsonPrimitive(size.name))) },
                actions = listOf(
                    PuppetAction(Resize.serializer()) { size = it.size },
                    PuppetAction(Reset.serializer()) { size = Size.Small },
                ),
            )
        },
    )

    private suspend fun send(line: String): JsonObject = Json.parseToJsonElement(host.handle(line)).jsonObject

    @Test
    fun `ls lists the focused node with each action's schema`() = runTest {
        val response = send("""{"cmd":"ls"}""")
        assertEquals(
            Json.parseToJsonElement(
                """
                {"ok":true,"path":["Box"],"node":{"name":"Box","state":{"size":"Small"},"actions":[
                  {"name":"Resize","input":{"type":"object","properties":{
                    "size":{"type":"string","enum":["Small","Large"]},
                    "note":{"type":"string","nullable":true}},"required":["size"]}},
                  {"name":"Reset","input":{"type":"object","properties":{},"required":[]}}
                ]}}
                """
            ),
            response,
        )
    }

    @Test
    fun `do performs the action and returns the new state`() = runTest {
        val response = send("""{"cmd":"do","action":"Resize","input":{"size":"Large"}}""")
        assertEquals("Large", response["node"]!!.jsonObject["state"]!!.jsonObject["size"]!!.jsonPrimitive.content)
        assertEquals("Large", send("""{"cmd":"ls"}""")["node"]!!.jsonObject["state"]!!.jsonObject["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an action without input can leave it out`() = runTest {
        size = Size.Large
        send("""{"cmd":"do","action":"Reset"}""")
        assertEquals(Size.Small, size)
    }

    @Test
    fun `an unknown action lists the available ones`() = runTest {
        val error = send("""{"cmd":"do","action":"Explode"}""")["error"]!!.jsonObject
        assertEquals("unknown_action", error["code"]!!.jsonPrimitive.content)
        assertEquals(listOf("Resize", "Reset"), error["actions"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `input that does not match the schema is bad_input and changes nothing`() = runTest {
        val error = send("""{"cmd":"do","action":"Resize","input":{"size":"Huge"}}""")["error"]!!.jsonObject
        assertEquals("bad_input", error["code"]!!.jsonPrimitive.content)
        assertEquals(Size.Small, size)
        val misspelled = send("""{"cmd":"do","action":"Resize","input":{"sise":"Large"}}""")["error"]!!.jsonObject
        assertEquals("bad_input", misspelled["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a line that is not a request is bad_request`() = runTest {
        for (line in listOf("ls", """{"cmd":"rm"}""", """{"cmd":"do"}""", "[]")) {
            assertEquals("bad_request", send(line)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content, line)
        }
    }

    @Test
    fun `reset runs the host's reset and returns the focused node`() = runTest {
        var resets = 0
        val resettable = PuppetHost(
            root = { PuppetNode("Box", { JsonObject(mapOf("size" to JsonPrimitive(size.name))) }, emptyList()) },
            reset = { resets++; size = Size.Small },
        )
        size = Size.Large
        val response = Json.parseToJsonElement(resettable.handle("""{"cmd":"reset"}""")).jsonObject
        assertEquals(1, resets)
        assertEquals("Small", response["node"]!!.jsonObject["state"]!!.jsonObject["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reset on a host without one is bad_request`() = runTest {
        assertEquals("bad_request", send("""{"cmd":"reset"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a fixture guard violation turns the response into an error`() = runTest {
        val guarded = PuppetHost(root = { PuppetNode("Box", { JsonObject(emptyMap()) }, emptyList()) }, violations = { listOf("network: ChatRepository.getChat") })
        val error = Json.parseToJsonElement(guarded.handle("""{"cmd":"ls"}""")).jsonObject["error"]!!.jsonObject
        assertEquals("fixture_violation", error["code"]!!.jsonPrimitive.content)
        assertEquals(listOf("network: ChatRepository.getChat"), error["calls"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    private class Navigating {
        val stack = PuppetStack()
        var resets = 0
        val host = PuppetHost(
            root = {
                PuppetNode(
                    name = "List",
                    state = { JsonObject(emptyMap()) },
                    actions = listOf(PuppetAction(Reset.serializer()) { stack.push(PuppetNode("Detail", { JsonObject(mapOf("id" to JsonPrimitive("7"))) }, emptyList())) }),
                )
            },
            stack = stack,
            launchRoutes = setOf("/tab/list"),
            reset = { resets++ },
        )

        suspend fun send(line: String): JsonObject = Json.parseToJsonElement(host.handle(line)).jsonObject
    }

    private fun JsonObject.path(): List<String> = this["path"]!!.jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.actionNames(): List<String> = this["node"]!!.jsonObject["actions"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
    private fun JsonObject.errorCode(): String = this["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    @Test
    fun `a pushed screen is focused and lends Back, which pops it`() = runTest {
        val nav = Navigating()
        val pushed = nav.send("""{"cmd":"do","action":"Reset"}""")
        assertEquals(listOf("List", "Detail"), pushed.path())
        assertEquals(listOf("Back"), pushed.actionNames())
        assertEquals("7", pushed["node"]!!.jsonObject["state"]!!.jsonObject["id"]!!.jsonPrimitive.content)

        val back = nav.send("""{"cmd":"do","action":"Back"}""")
        assertEquals(listOf("List"), back.path())
        assertEquals(listOf("Reset"), back.actionNames())
    }

    @Test
    fun `Back at the root is unknown_action`() = runTest {
        assertEquals("unknown_action", Navigating().send("""{"cmd":"do","action":"Back"}""").errorCode())
    }

    @Test
    fun `launch resets, clears the stack and accepts only the hosted route`() = runTest {
        val nav = Navigating()
        nav.send("""{"cmd":"do","action":"Reset"}""")
        val launched = nav.send("""{"cmd":"launch","route":"/tab/list"}""")
        assertEquals(listOf("List"), launched.path())
        assertEquals(1, nav.resets)
        assertEquals(listOf("List"), nav.send("""{"cmd":"launch"}""").path())
        assertEquals("bad_input", nav.send("""{"cmd":"launch","route":"/tab/elsewhere"}""").errorCode())
        assertEquals(2, nav.resets)
    }
}
