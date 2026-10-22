package com.flipcash.app.hotswap

import android.content.Context
import android.util.Log
import dalvik.system.BaseDexClassLoader
import java.io.File

/**
 * RedefineClasses cannot add a class, but an edited function often brings new ones (d8 names a desugared
 * lambda `Foo$0`, which the installed APK does not have). Those go in through the app class loader's own
 * dex path, so a redefined class that refers to them resolves them like any other. Plain reflection on
 * `pathList`/`addDexPath`, which the platform lists as unsupported rather than blocked.
 */
internal object ClassInjector {
    private const val TAG = "HotSwap"

    /** Adds the dex to [context]'s class loader. The file must be read-only before ART loads it (API 34+). */
    fun inject(context: Context, name: String, dex: ByteArray): Boolean {
        return try {
            val dir = File(context.codeCacheDir, "hotswap-inject").apply { mkdirs() }
            val f = File(dir, "${System.nanoTime()}.dex")
            f.writeBytes(dex)
            f.setReadOnly()
            val loader = context.classLoader as BaseDexClassLoader
            val pathList = BaseDexClassLoader::class.java.getDeclaredField("pathList").apply { isAccessible = true }.get(loader)
            val add = pathList.javaClass.getDeclaredMethod("addDexPath", String::class.java, File::class.java).apply { isAccessible = true }
            add.invoke(pathList, f.absolutePath, null)
            Log.i(TAG, "injected $name")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "inject $name failed", t)
            false
        }
    }
}
