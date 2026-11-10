package com.flipcash.app.puppet.chats

import com.flipcash.app.puppet.flipcashHost
import com.flipcash.libs.coroutines.DispatcherProvider
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays the shared behavior files for the Chats screen (`test-vectors/behaviors/chats/`) through
 * the app host, the same `handle()` the CLI's process calls, against the real view model over the
 * seeded fixture. The iOS app runs the same files against its own
 * view model, so a field or decision that drifts fails on whichever side changed.
 *
 * Each file starts from the seed. A step sends `ls` or `do` through the Puppet protocol and checks
 * `expect` as a subset of the state, or `expectError` as the error code. Gradle passes the files as
 * `puppet.behaviors`, a directory or one file (`-Ppuppet.behaviors=<path>` overrides it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsBehaviorTest {

    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every behavior file passes`() {
        val files = behaviorFiles()
        assertTrue(files.isNotEmpty(), "no behavior files at ${System.getProperty(PROPERTY)}")
        val failures = files.flatMap { file -> replay(file).map { "${file.name}: $it" } }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n", prefix = "${failures.size} failed:\n"))
    }

    private fun replay(file: File): List<String> {
        val spec = Json.parseToJsonElement(file.readText()).jsonObject
        val failures = mutableListOf<String>()
        runTest(main) {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val dispatchers = object : DispatcherProvider {
                override val Default: CoroutineDispatcher = dispatcher
                override val Main: CoroutineDispatcher = dispatcher
                override val IO: CoroutineDispatcher = dispatcher
            }
            val host = flipcashHost(dispatchers, settle = { advanceUntilIdle() })
            val launch = buildJsonObject {
                put("cmd", "launch")
                spec["route"]?.let { put("route", it) }
            }
            val launched = Json.parseToJsonElement(host.handle(launch.toString())).jsonObject
            val screen = spec["screen"]?.jsonPrimitive?.content
            when {
                !launched.getValue("ok").jsonPrimitive.boolean -> {
                    failures += "launch: ${launched["error"]}"
                    host.close()
                    return@runTest
                }
                screen != null && launched.getValue("node").jsonObject.getValue("name").jsonPrimitive.content != screen ->
                    failures += "launch: expected screen $screen, got ${launched.getValue("node").jsonObject["name"]}"
            }
            spec.getValue("steps").let { it as kotlinx.serialization.json.JsonArray }.forEachIndexed { index, element ->
                val step = element.jsonObject
                val label = "step ${index + 1} (${step["do"]?.jsonPrimitive?.content ?: "ls"})"
                val response = Json.parseToJsonElement(host.handle(request(step).toString())).jsonObject
                val ok = response.getValue("ok").jsonPrimitive.boolean
                val error = response["error"]?.jsonObject
                val expectedError = step["expectError"]?.jsonPrimitive?.content
                when {
                    expectedError != null -> {
                        val code = error?.get("code")?.jsonPrimitive?.content
                        if (ok || code != expectedError) failures += "$label: expected error $expectedError, got $response"
                    }
                    !ok -> {
                        failures += "$label: ${error?.get("code")}: ${error?.get("message")}"
                        host.close()
                        return@runTest
                    }
                    else -> {
                        val node = response.getValue("node").jsonObject
                        step["expectPath"]?.let { expected ->
                            val path = response.getValue("path")
                            if (path != expected) failures += "$label: path: expected $expected, got $path"
                        }
                        step["expect"]?.let { failures += mismatches(it, node.getValue("state")).map { m -> "$label: $m" } }
                    }
                }
            }
            host.close()
        }
        return failures
    }

    private fun request(step: JsonObject): JsonObject = when {
        step["ls"]?.jsonPrimitive?.boolean == true -> buildJsonObject { put("cmd", "ls") }
        "do" in step -> buildJsonObject {
            put("cmd", "do")
            put("action", step.getValue("do"))
            put("input", step["input"] ?: JsonObject(emptyMap()))
        }
        else -> error("a step needs \"ls\": true or \"do\": <action>, got $step")
    }

    private companion object {
        const val PROPERTY = "puppet.behaviors"

        fun behaviorFiles(): List<File> {
            val path = File(checkNotNull(System.getProperty(PROPERTY)) { "$PROPERTY isn't set" })
            return if (path.isDirectory) path.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name } else listOf(path)
        }

        /** Every key [expected] names must be in [actual] with an equal value; objects match recursively. */
        fun mismatches(expected: JsonElement, actual: JsonElement?, where: String = "state"): List<String> = when {
            expected is JsonObject && actual !is JsonObject -> listOf("$where: expected an object, got $actual")
            expected is JsonObject -> expected.flatMap { (key, value) ->
                val inner = (actual as JsonObject)[key]
                if (inner == null) listOf("$where.$key: missing") else mismatches(value, inner, "$where.$key")
            }
            expected != actual -> listOf("$where: expected $expected, got $actual")
            else -> emptyList()
        }
    }
}
