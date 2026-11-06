package com.flipcash.desktop.reflex

import com.flipcash.app.core.data.Loadable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.libs.reflex.ReflexHost
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
 * JSON response per line on stdout (see [ReflexHost] for the protocol). Run it with
 * `./gradlew -q :apps:desktop:reflex`.
 *
 * Exits with status 2 after answering a request during which the fixture guard caught a call
 * past the fixture, so a scripted session can't miss one.
 */
fun main() {
    // Before anything touches AppProcessLifecycle or AWT: no lifecycle start, which would open the
    // reserve stream, and no display.
    System.setProperty("opencode.processLifecycle.autoStart", "false")
    System.setProperty("java.awt.headless", "true")

    val fixture = ChatListFixture(
        dispatchers = object : DispatcherProvider {
            override val Default: CoroutineDispatcher = Dispatchers.Default
            override val Main: CoroutineDispatcher = Dispatchers.Main
            override val IO: CoroutineDispatcher = Dispatchers.IO
        },
    )
    val host = chatsHost(fixture) {
        // The first feed mapping runs on Default; wait for it, then let follow-on work land.
        withTimeoutOrNull(5_000) {
            while (fixture.viewModel.stateFlow.value.chats is Loadable.Loading) delay(10)
        }
        delay(50)
    }

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

/** The Reflex host over [fixture]'s Chats screen; [settle] waits for the view model's work. */
fun chatsHost(fixture: ChatListFixture, settle: suspend () -> Unit): ReflexHost = ReflexHost(
    root = { chatsNode(fixture.viewModel) },
    settle = settle,
    violations = fixture.guard::drain,
)
