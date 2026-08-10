package com.getcode.ed25519kmp

actual fun readTestResource(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader?.getResourceAsStream(name)) {
        "Resource '$name' not found on classpath"
    }.bufferedReader().use { it.readText() }
