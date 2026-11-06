package com.flipcash.desktop.puppet

import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.repository.ChatRepository
import com.getcode.view.PublicAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives the Chats screen through the Puppet line protocol end to end, over the seeded fixture,
 * with the same request lines the CLI reads from stdin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsPuppetTest {

    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.session(): Pair<ChatListFixture, suspend (String) -> JsonObject> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val fixture = ChatListFixture(
            dispatchers = object : DispatcherProvider {
                override val Default: CoroutineDispatcher = dispatcher
                override val Main: CoroutineDispatcher = dispatcher
                override val IO: CoroutineDispatcher = dispatcher
            },
        )
        val host = chatsHost(fixture) { advanceUntilIdle() }
        return fixture to { line -> Json.parseToJsonElement(host.handle(line)).jsonObject }
    }

    private val JsonObject.state get() = this["node"]!!.jsonObject["state"]!!.jsonObject
    private fun JsonObject.strings(key: String) = this[key]!!.jsonArray.map { it.jsonPrimitive.content }

    private val ballers = ChatListFixture.chatId(1).hex()
    private val ava = ChatListFixture.chatId(2).hex()
    private val bookClub = ChatListFixture.chatId(3).hex()
    private val sam = ChatListFixture.chatId(4).hex()

    @Test
    fun `ls, a filter change, ls again, and an unknown action`() = runTest(main) {
        val (fixture, send) = session()

        val first = send("""{"cmd":"ls"}""")
        assertEquals(listOf("Chats"), first.strings("path"))
        val node = first["node"]!!.jsonObject
        assertEquals("Chats", node["name"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("FilterSelected", "ChipsRevealed", "SelectionStarted", "SelectionToggled", "SelectionEnded"),
            node["actions"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
        val filterInput = node["actions"]!!.jsonArray.first().jsonObject["input"]!!.jsonObject
        assertEquals(
            Json.parseToJsonElement(
                """{"type":"object","properties":{"filter":{"type":"string","enum":["All","Unread","Groups"]}},"required":["filter"]}"""
            ),
            filterInput,
        )
        with(first.state) {
            assertEquals("All", this["filter"]!!.jsonPrimitive.content)
            assertEquals(false, this["showsChips"]!!.jsonPrimitive.boolean)
            assertEquals(listOf(ballers, ava, bookClub, sam), strings("visibleChatIds"))
            assertEquals(1, this["archivedRowCount"]!!.jsonPrimitive.content.toInt())
        }

        val afterDo = send("""{"cmd":"do","action":"FilterSelected","input":{"filter":"Unread"}}""")
        assertEquals(listOf(ballers, sam), afterDo.state.strings("visibleChatIds"))

        val second = send("""{"cmd":"ls"}""")
        with(second.state) {
            assertEquals("Unread", this["filter"]!!.jsonPrimitive.content)
            assertEquals(true, this["chipsRevealed"]!!.jsonPrimitive.boolean)
            assertEquals(true, this["showsChips"]!!.jsonPrimitive.boolean)
            assertEquals(listOf(ballers, sam), strings("visibleChatIds"))
        }

        val unknown = send("""{"cmd":"do","action":"ArchiveEverything","input":{}}""")
        assertEquals(false, unknown["ok"]!!.jsonPrimitive.boolean)
        val error = unknown["error"]!!.jsonObject
        assertEquals("unknown_action", error["code"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("FilterSelected", "ChipsRevealed", "SelectionStarted", "SelectionToggled", "SelectionEnded"),
            error.strings("actions"),
        )

        assertEquals(emptyList(), fixture.guard.drain(), "the session reached past the fixture")
    }

    @Test
    fun `select mode takes chat ids in hex`() = runTest(main) {
        val (fixture, send) = session()
        send("""{"cmd":"do","action":"SelectionStarted"}""")
        val toggled = send("""{"cmd":"do","action":"SelectionToggled","input":{"chatId":"$bookClub"}}""").state
        assertEquals(true, toggled["isSelecting"]!!.jsonPrimitive.boolean)
        assertEquals(listOf(bookClub), toggled.strings("selection"))
        val ended = send("""{"cmd":"do","action":"SelectionEnded"}""").state
        assertEquals(false, ended["isSelecting"]!!.jsonPrimitive.boolean)
        assertEquals(emptyList(), fixture.guard.drain())
    }

    @Test
    fun `the node lists every public action and nothing else`() {
        val public = ChatsViewModel.Event::class.java.declaredClasses
            .filter { ChatsViewModel.Event::class.java.isAssignableFrom(it) && PublicAction::class.java.isAssignableFrom(it) }
            .map { it.simpleName }
            .toSet()
        val listed = chatsNode(session0().viewModel).actions.map { it.name }.toSet()
        assertEquals(public, listed)
    }

    @Test
    fun `a repository call records a violation and throws`() {
        val guard = FixtureGuard()
        val repository = guard.network<ChatRepository>()
        // Object methods answer without recording, so logging or hashing a stand-in is safe.
        repository.hashCode()
        repository.toString()
        assertEquals(emptyList(), guard.drain())
        assertFailsWith<FixtureViolation> { callFirstMethod(repository) }
        assertTrue(guard.drain().single().startsWith("network: ChatRepository."))
        val unfaked = guard.unfaked<ChatKeySource>()
        assertEquals("unfaked ChatKeySource", unfaked.toString())
        assertEquals(emptyList(), guard.drain())
    }

    @Test
    fun `a violation during a request is reported as fixture_violation`() = runTest(main) {
        val (fixture, send) = session()
        fixture.guard.record("network: ChatRepository.getChat")
        val error = send("""{"cmd":"ls"}""")["error"]!!.jsonObject
        assertEquals("fixture_violation", error["code"]!!.jsonPrimitive.content)
        assertEquals(listOf("network: ChatRepository.getChat"), error.strings("calls"))
    }

    private fun session0() = ChatListFixture(
        dispatchers = object : DispatcherProvider {
            override val Default: CoroutineDispatcher = main
            override val Main: CoroutineDispatcher = main
            override val IO: CoroutineDispatcher = main
        },
    )

    /** Calls the first abstract method of [stub]'s interface, with null or zero for each argument. */
    private fun callFirstMethod(stub: Any) {
        val method = stub.javaClass.interfaces.single().methods.first { java.lang.reflect.Modifier.isAbstract(it.modifiers) }
        val args = method.parameterTypes.map { type ->
            when (type) {
                Boolean::class.javaPrimitiveType -> false
                Char::class.javaPrimitiveType -> ' '
                Byte::class.javaPrimitiveType -> 0.toByte()
                Short::class.javaPrimitiveType -> 0.toShort()
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }
        }
        try {
            method.invoke(stub, *args.toTypedArray<Any?>())
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun com.flipcash.services.models.chat.ChatId.hex(): String = bytes.toHexString()
