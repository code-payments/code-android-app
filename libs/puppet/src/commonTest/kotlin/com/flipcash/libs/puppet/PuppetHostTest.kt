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

    private fun single(session: PuppetSession) = PuppetHost(mapOf("/box" to { _, _ -> session }))

    private val host = single(
        PuppetSession(
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
        ),
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
    fun `reset relaunches the first route and returns the focused node`() = runTest {
        var starts = 0
        var closes = 0
        val resettable = PuppetHost(
            mapOf(
                "/box" to { _, _ ->
                    starts++
                    size = Size.Small
                    PuppetSession(
                        root = { PuppetNode("Box", { JsonObject(mapOf("size" to JsonPrimitive(size.name))) }, emptyList()) },
                        close = { closes++ },
                    )
                },
            ),
        )
        size = Size.Large
        val response = Json.parseToJsonElement(resettable.handle("""{"cmd":"reset"}""")).jsonObject
        assertEquals(2, starts)
        assertEquals(1, closes)
        assertEquals("Small", response["node"]!!.jsonObject["state"]!!.jsonObject["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a fixture guard violation turns the response into an error`() = runTest {
        val guarded = PuppetHost(
            mapOf("/box" to { _, _ -> PuppetSession(root = { PuppetNode("Box", { JsonObject(emptyMap()) }, emptyList()) }, violations = { listOf("network: ChatRepository.getChat") }) }),
            violations = { listOf("network: chat.example") },
        )
        val error = Json.parseToJsonElement(guarded.handle("""{"cmd":"ls"}""")).jsonObject["error"]!!.jsonObject
        assertEquals("fixture_violation", error["code"]!!.jsonPrimitive.content)
        assertEquals(listOf("network: ChatRepository.getChat", "network: chat.example"), error["calls"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    /** Two routes: a list that pushes a detail screen, and a settings screen. */
    private class Navigating {
        var listStarts = 0
        var closes = 0
        val host = PuppetHost(
            mapOf(
                "/tab/list" to { stack, _ ->
                    listStarts++
                    PuppetSession(
                        root = {
                            PuppetNode(
                                name = "List",
                                state = { JsonObject(emptyMap()) },
                                actions = listOf(PuppetAction(Reset.serializer()) { stack.push(PuppetNode("Detail", { JsonObject(mapOf("id" to JsonPrimitive("7"))) }, emptyList())) }),
                            )
                        },
                        close = { closes++ },
                    )
                },
                "/tab/settings" to { _, _ -> PuppetSession(root = { PuppetNode("Settings", { JsonObject(emptyMap()) }, emptyList()) }, close = { closes++ }) },
            ),
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
    fun `launch starts the route's session, closes the last one and clears the stack`() = runTest {
        val nav = Navigating()
        nav.send("""{"cmd":"do","action":"Reset"}""")
        assertEquals(listOf("List"), nav.send("""{"cmd":"launch","route":"/tab/list"}""").path())
        assertEquals(2, nav.listStarts)
        assertEquals(listOf("Settings"), nav.send("""{"cmd":"launch","route":"/tab/settings"}""").path())
        assertEquals(listOf("List"), nav.send("""{"cmd":"launch"}""").path())
        assertEquals(3, nav.closes)
    }

    @Test
    fun `launch of a route the host doesn't serve is bad_input and keeps the session`() = runTest {
        val nav = Navigating()
        nav.send("""{"cmd":"do","action":"Reset"}""")
        assertEquals("bad_input", nav.send("""{"cmd":"launch","route":"/tab/elsewhere"}""").errorCode())
        assertEquals(0, nav.closes)
        assertEquals(listOf("List", "Detail"), nav.send("""{"cmd":"ls"}""").path())
    }

    /** A host whose one route shows the seed it was launched with. */
    private fun seeded(): PuppetHost = PuppetHost(
        routes = mapOf(
            "/box" to { _, seed -> PuppetSession(root = { PuppetNode("Box", { JsonObject(mapOf("seed" to JsonPrimitive(seed))) }, emptyList()) }) },
        ),
        seeds = listOf("default", "full"),
    )

    private suspend fun PuppetHost.seedShown(line: String): String =
        Json.parseToJsonElement(handle(line)).jsonObject["node"]!!.jsonObject["state"]!!.jsonObject["seed"]!!.jsonPrimitive.content

    @Test
    fun `launch passes its seed to the route, and the first seed when it names none`() = runTest {
        val host = seeded()
        assertEquals("full", host.seedShown("""{"cmd":"launch","seed":"full"}"""))
        assertEquals("default", host.seedShown("""{"cmd":"launch"}"""))
        assertEquals("full", host.seedShown("""{"cmd":"launch","route":"/box","seed":"full"}"""))
        assertEquals("default", host.seedShown("""{"cmd":"reset"}"""))
    }

    @Test
    fun `launch of a seed the host doesn't have is bad_input, and a non-string seed is bad_request`() = runTest {
        val host = seeded()
        host.handle("""{"cmd":"launch","seed":"full"}""")
        assertEquals("bad_input", Json.parseToJsonElement(host.handle("""{"cmd":"launch","seed":"empty"}""")).jsonObject.errorCode())
        assertEquals("bad_request", Json.parseToJsonElement(host.handle("""{"cmd":"launch","seed":3}""")).jsonObject.errorCode())
        assertEquals("full", host.seedShown("""{"cmd":"ls"}"""))
    }
}
