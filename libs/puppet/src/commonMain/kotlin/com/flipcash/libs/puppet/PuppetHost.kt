package com.flipcash.libs.puppet

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal object PuppetJson {
    /** Action input: strict, so a misspelled field is an error rather than a silent default. */
    val input = Json { ignoreUnknownKeys = false }

    /** Responses: one line each, defaults and nulls written out so every field is always there. */
    val output = Json { encodeDefaults = true; explicitNulls = true; prettyPrint = false }
}

/**
 * What a launched route runs until the next `launch`: its root screen and the hooks the host calls
 * around each request. [settle] runs after each action and before each listing, so the state
 * reflects the work the action started. [violations] returns the calls the route's fixture guard
 * caught since the last request. [close] runs when another `launch` replaces the session.
 */
class PuppetSession(
    val root: () -> PuppetNode,
    val settle: suspend () -> Unit = {},
    val violations: () -> List<String> = { emptyList() },
    val close: () -> Unit = {},
)

/**
 * Answers the Puppet line protocol for one app: every screen it hosts, keyed by route. One request
 * per line, one response per line:
 *
 * - `{"cmd":"ls"}` returns the focused screen.
 * - `{"cmd":"launch","route":"/tab/chats"}` closes the current session, empties [stack] and starts
 *   the route's session from [routes], then returns the focused screen. A runner uses it to reuse
 *   one host for many behavior files. `route` may be left out for the first route; a route not in
 *   [routes] is `bad_input`.
 * - `{"cmd":"reset"}` is `launch` without a route, kept for runners that predate it.
 * - `{"cmd":"do","action":"<name>","input":{...}}` sends an action to the focused screen and returns
 *   the focused screen after it. `input` may be left out for an action that takes none.
 *
 * Every response has `"ok"`. A success carries `"path"` (screen names from the root to the focused
 * screen) and `"node"` (`name`, `state`, `actions` with each one's input schema). A failure carries
 * `"error"` with a `code` (`bad_request`, `unknown_action`, `bad_input`, `fixture_violation`) and a
 * `message`; `unknown_action` also lists the focused screen's actions.
 *
 * Calls caught by the session's guard or by the app-wide [violations] (such as blocked network
 * connections) turn the response into a `fixture_violation` error listing them under `calls`.
 *
 * A session's view models push the screens they navigate to onto [stack], which it receives when it
 * starts. They follow the root's own [PuppetNode.child] chain in `path`, the top one is focused, and
 * the host adds `Back` to its actions.
 *
 * The first session starts when the host is built.
 */
class PuppetHost(
    private val routes: Map<String, (PuppetStack) -> PuppetSession>,
    private val violations: () -> List<String> = { emptyList() },
) {
    init {
        require(routes.isNotEmpty()) { "A Puppet host needs at least one route" }
    }

    private val stack = PuppetStack()
    private var session: PuppetSession = routes.values.first()(stack)

    private val back = PuppetAction(Back.serializer()) { stack.pop() }

    /** Closes the current session, for a caller that is done with the host. */
    fun close() = session.close()

    suspend fun handle(line: String): String = PuppetJson.output.encodeToString(JsonObject.serializer(), respond(line))

    private suspend fun respond(line: String): JsonObject {
        val request = try {
            PuppetJson.input.parseToJsonElement(line) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return error("bad_request", "Expected one JSON object per line, got: $line")

        val response = when (request.string("cmd")) {
            "ls" -> focusedResponse()
            "do" -> perform(request)
            "launch" -> launch(request.string("route"))
            "reset" -> launch(null)
            else -> error("bad_request", "Unknown cmd ${request["cmd"]}; expected \"ls\", \"do\", \"launch\" or \"reset\"")
        }
        val caught = session.violations() + violations()
        if (caught.isNotEmpty()) {
            return error("fixture_violation", "The screen reached past its fixture: ${caught.joinToString()}") {
                putJsonArray("calls") { caught.forEach { add(it) } }
            }
        }
        return response
    }

    private suspend fun launch(route: String?): JsonObject {
        val start = routes[route ?: routes.keys.first()]
            ?: return error("bad_input", "This host launches ${routes.keys.joinToString()}, not $route")
        session.close()
        stack.clear()
        session = start(stack)
        return focusedResponse()
    }

    private suspend fun perform(request: JsonObject): JsonObject {
        val name = request.string("action") ?: return error("bad_request", "\"do\" needs an \"action\"")
        val focused = screens().last()
        val actions = actionsOf(focused)
        val action = actions.firstOrNull { it.name == name } ?: return error(
            "unknown_action",
            "${focused.name} has no action $name",
        ) {
            putJsonArray("actions") { actions.forEach { add(it.name) } }
        }
        val input = request["input"]?.takeUnless { it is JsonNull } ?: JsonObject(emptyMap())
        // SerializationException is an IllegalArgumentException, as are the require() checks in a
        // value's constructor. Only decoding sits inside the try, so a failure in the action itself
        // is not reported as bad input.
        val call = try {
            action.bind(input)
        } catch (e: IllegalArgumentException) {
            return error("bad_input", "Input for $name does not match its schema: ${e.message}") {
                put("schema", action.serializer.descriptor.inputSchema())
            }
        }
        call()
        return focusedResponse()
    }

    private suspend fun focusedResponse(): JsonObject {
        session.settle()
        val path = screens()
        val node = path.last()
        val actions = actionsOf(node)
        return buildJsonObject {
            put("ok", true)
            putJsonArray("path") { path.forEach { add(it.name) } }
            put("node", buildJsonObject {
                put("name", node.name)
                put("state", node.state())
                putJsonArray("actions") {
                    actions.forEach { action ->
                        add(buildJsonObject {
                            put("name", action.name)
                            put("input", action.serializer.descriptor.inputSchema())
                        })
                    }
                }
            })
        }
    }

    private fun screens(): List<PuppetNode> = generateSequence(session.root()) { it.child() }.toList() + stack.pushed

    // Back is lent only while the stack has a screen; the root chain has nowhere to go back to.
    private fun actionsOf(node: PuppetNode): List<PuppetAction<*>> =
        if (stack.pushed.isNotEmpty()) node.actions + back else node.actions

    private fun error(
        code: String,
        message: String,
        extra: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
            extra()
        })
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
