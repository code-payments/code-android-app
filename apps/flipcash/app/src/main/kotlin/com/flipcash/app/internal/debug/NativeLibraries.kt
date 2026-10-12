package com.flipcash.app.internal.debug

import dalvik.system.BaseDexClassLoader

/**
 * Whether the install carries the native libraries the APK was built with.
 *
 * An install missing its ABI split has none of them, so the first library to load one crashes:
 * build 4804 died in `LibSodiumInitializer` with `UnsatisfiedLinkError` for `libjnidispatch.so` on
 * a Pixel 6 Pro (`6aafa68958142669ea5db3c7`), and Bugsnag reported its own NDK and ANR plugins
 * failing to link from the same device. Nothing in the app can fix the install, but a real user
 * can end up with one, so the event is tagged rather than discarded.
 */
internal object NativeLibraries {

    /**
     * One library from a dependency we always load at startup, and one from Bugsnag: if both are
     * missing, the install is missing all of them.
     */
    private val Probes = listOf("jnidispatch", "bugsnag-ndk")

    /** Probed once per process. */
    val missing: List<String> by lazy {
        val loader = NativeLibraries::class.java.classLoader as? BaseDexClassLoader
        if (loader == null) emptyList() else missingLibraries(Probes, loader::findLibrary)
    }
}

/**
 * The [names] that [findLibrary] cannot resolve.
 *
 * `findLibrary` resolves a library from the extracted lib directory or straight from the APK, and
 * loads nothing, so probing has no side effects. It is injected so this stays a pure function
 * under unit test.
 */
internal fun missingLibraries(names: List<String>, findLibrary: (String) -> String?): List<String> =
    names.filter { name ->
        try {
            findLibrary(name) == null
        } catch (_: Throwable) {
            true
        }
    }
