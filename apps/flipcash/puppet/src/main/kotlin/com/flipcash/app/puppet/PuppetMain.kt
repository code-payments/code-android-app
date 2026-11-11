package com.flipcash.app.puppet

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.system.exitProcess

/**
 * The app's Puppet host as a process: one JSON request per line on stdin, one response per line on
 * stdout (`docs/puppet/PROTOCOL.md` in the orchestrator). The orchestrator's `puppet` CLI starts
 * it through the launcher `:apps:flipcash:puppet:puppetLauncher` writes, and keeps it running
 * across behavior files, sending `launch` between them.
 *
 * Every dispatcher is one test scheduler, and each response waits for `advanceUntilIdle`, so a
 * reply always reflects all the work the request started, with no wall-clock wait. It exits with
 * status 2 after answering a request during which the fixture guard caught a call past the
 * fixture, so a session can't miss one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    val dispatchers = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = dispatcher
        override val Main: CoroutineDispatcher = dispatcher
        override val IO: CoroutineDispatcher = dispatcher
    }
    val host = flipcashHost(dispatchers, settle = { scheduler.advanceUntilIdle() })

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
