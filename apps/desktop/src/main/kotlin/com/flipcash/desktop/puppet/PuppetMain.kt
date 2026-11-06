package com.flipcash.desktop.puppet

import com.flipcash.app.core.data.Loadable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.libs.puppet.PuppetHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.system.exitProcess

/**
 * Drives the Chats screen headlessly over seeded data: one JSON request per line on stdin, one
 * JSON response per line on stdout (see [PuppetHost] for the protocol). Run it with
 * `./gradlew -q :apps:desktop:puppet`.
 *
 * Exits with status 2 after answering a request during which the fixture guard caught a call
 * past the fixture, so a scripted session can't miss one.
 */
fun main() {
    // Before anything touches AppProcessLifecycle or AWT: no lifecycle start, which would open the
    // reserve stream, and no display.
    System.setProperty("opencode.processLifecycle.autoStart", "false")
    System.setProperty("java.awt.headless", "true")

    val dispatchers = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = Dispatchers.Default
        override val Main: CoroutineDispatcher = Dispatchers.Main
        override val IO: CoroutineDispatcher = Dispatchers.IO
    }
    val host = chatsHost(newFixture = { ChatListFixture(dispatchers) })

    runBlocking {
        generateSequence(::readLine).filter { it.isNotBlank() }.forEach { line ->
            val response = host.handle(line)
            println(response)
            System.out.flush()
            val code = Json.parseToJsonElement(response).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            if (code == "fixture_violation") exitProcess(2)
        }
    }
    exitProcess(0)
}

/**
 * The Puppet host over a Chats fixture from [newFixture]. `reset` closes the current fixture and
 * builds a fresh one, so a runner can reuse one JVM for many behavior files. [settle] waits for the
 * current fixture's view model before each response.
 */
fun chatsHost(
    newFixture: () -> ChatListFixture,
    settle: suspend (ChatListFixture) -> Unit = ::awaitLoaded,
): PuppetHost {
    var fixture = newFixture()
    return PuppetHost(
        root = { chatsNode(fixture.viewModel) },
        settle = { settle(fixture) },
        violations = { fixture.guard.drain() },
        reset = {
            fixture.close()
            fixture = newFixture()
        },
    )
}

/** The first feed mapping runs on Default; wait for it, then let follow-on work land. */
private suspend fun awaitLoaded(fixture: ChatListFixture) {
    withTimeoutOrNull(5_000) {
        while (fixture.viewModel.stateFlow.value.chats is Loadable.Loading) delay(10)
    }
    delay(50)
}
