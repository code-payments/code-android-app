package com.flipcash.app.puppet

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Records every call that gets past the Puppet fixture. The fixture has no network channel: every
 * repository (the gRPC-facing interfaces) is a [network] stand-in, and every other seam it doesn't
 * fake is an [unfaked] one. Both record the call here and then throw.
 *
 * Throwing alone would be silent: much of the app wraps these calls in `runCatching` or
 * `retryable`, which catch everything. So the host reads [drain] after each request and turns
 * any entry into an error, and the tests assert the ledger is empty.
 */
class FixtureGuard {
    private val calls = mutableListOf<String>()

    @Synchronized
    fun record(call: String) {
        calls += call
    }

    /** The calls caught since the last drain, oldest first. */
    @Synchronized
    fun drain(): List<String> = calls.toList().also { calls.clear() }

    /** A repository: any call would go to the backend. */
    inline fun <reified T : Any> network(): T = proxy(T::class.java, "network")

    /** A seam the fixture doesn't fake: any call means the screen needs more fixture. */
    inline fun <reified T : Any> unfaked(): T = proxy(T::class.java, "unfaked")

    fun <T : Any> proxy(type: Class<T>, kind: String): T {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "toString" -> if (args.isNullOrEmpty()) return@InvocationHandler "$kind ${type.simpleName}" else Unit
                "hashCode" -> if (args.isNullOrEmpty()) return@InvocationHandler System.identityHashCode(proxy) else Unit
                "equals" -> if (args?.size == 1) return@InvocationHandler proxy === args[0] else Unit
            }
            val call = "$kind: ${type.simpleName}.${method.name}"
            record(call)
            throw FixtureViolation(call)
        }
        return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
    }
}

class FixtureViolation(call: String) : IllegalStateException("Puppet fixture guard: $call")
