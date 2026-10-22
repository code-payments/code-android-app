package com.flipcash.app.hotswap

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File
import java.util.zip.ZipFile

/** The JVMTI agent in `libhotswap_agent.so`: attached to this process, it redefines classes. */
internal object JvmtiAgent {
    private const val LIB = "libhotswap_agent.so"

    @JvmStatic external fun attachStatus(): Int
    @JvmStatic external fun redefine(classes: Array<Class<*>>, dexes: Array<ByteArray>): Int

    /** Copies the agent out of the APK (it is not extracted) and attaches it. Throws on failure. */
    fun attach(context: Context) {
        val dir = context.codeCacheDir
        val out = File(dir, LIB)
        out.delete() // a previous run left it read-only
        ZipFile(context.applicationInfo.sourceDir).use { zip ->
            val abi = Build.SUPPORTED_ABIS.firstOrNull { zip.getEntry("lib/$it/$LIB") != null }
                ?: error("no $LIB in the APK for ${Build.SUPPORTED_ABIS.toList()}")
            zip.getInputStream(zip.getEntry("lib/$abi/$LIB")).use { i -> out.outputStream().use { i.copyTo(it) } }
        }
        out.setReadOnly() // System.load refuses a writable file on targetSdk 34+
        Debug.attachJvmtiAgent(out.absolutePath, null, context.classLoader)
        // Binds the native methods above; the library is already mapped for this loader.
        System.load(out.absolutePath)
        check(attachStatus() == 0) { "agent attach status ${attachStatus()}" }
    }

    fun errorName(code: Int): String = when (code) {
        0 -> "OK"
        60 -> "INVALID_CLASS_FORMAT"
        61 -> "CIRCULAR_CLASSDEF"
        62 -> "FAILS_VERIFICATION"
        63 -> "UNSUPPORTED_REDEFINITION_METHOD_ADDED"
        64 -> "UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED"
        65 -> "INVALID_TYPESTATE"
        66 -> "UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED"
        67 -> "UNSUPPORTED_REDEFINITION_METHOD_DELETED"
        68 -> "UNSUPPORTED_VERSION"
        69 -> "NAMES_DONT_MATCH"
        70 -> "UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED"
        71 -> "UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED"
        98 -> "NOT_AVAILABLE"
        99 -> "MUST_POSSESS_CAPABILITY"
        111 -> "ACCESS_DENIED"
        else -> "JVMTI_$code"
    }
}
