package com.getcode.ed25519kmp

import androidx.test.platform.app.InstrumentationRegistry

actual fun readTestResource(name: String): String =
    InstrumentationRegistry.getInstrumentation().context.assets
        .open(name).bufferedReader().use { it.readText() }
