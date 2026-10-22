package com.flipcash.app.hotswap

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Debug-only live patching. Long-polls `<base>/next?after=<seq>` (the wire protocol of the iOS
 * hotswap spike), downloads each class's dex from `/patches/<file>`, redefines it through
 * [JvmtiAgent] and reports the outcome to `/report`.
 */
internal object Hotswap {
    private const val TAG = "Hotswap"
    private const val DEFAULT_BASE = "http://100.91.141.96:18492"

    @Volatile private var started = false
    private const val RECOMPOSE_CAP_MS = 5000L
    private const val QUIET_MS = 100L

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext ?: context
        Thread({ run(app) }, "hotswap").apply { isDaemon = true }.start()
    }

    private fun run(context: Context) {
        val base = context.packageManager
            .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getString("hotswap.base") ?: DEFAULT_BASE
        Watchdog.start { report(base, it) }
        // Retry: a failed attach (server not up yet, a stale file) must not end the loader for the life of the process.
        var attached = false
        var attempt = 0
        while (!attached) {
            attempt++
            try {
                JvmtiAgent.attach(context)
                attached = true
                Log.i(TAG, "agent attached (attempt $attempt)")
            } catch (t: Throwable) {
                Log.e(TAG, "agent attach failed (attempt $attempt)", t)
                report(base, JSONObject().put("event", "attach").put("ok", false).put("attempt", attempt).put("error", t.toString()))
                if (attempt >= 5) return
                Thread.sleep(2000L * attempt)
            }
        }
        report(base, JSONObject().put("event", "attach").put("ok", true).put("attempt", attempt))
        // The server's notice is cumulative (every class changed since the watcher started), so a relaunched
        // process, which runs the installed APK again, catches up by applying the latest one.
        var seq = 0L
        while (true) {
            try {
                val notice = get(base + "/next?after=$seq")
                if (notice == null) {
                    // 204 after the long poll: if the server restarted its counter is lower than ours.
                    val cur = JSONObject(String(get("$base/seq") ?: continue)).getLong("seq")
                    if (cur < seq) seq = 0
                    continue
                }
                val json = JSONObject(String(notice))
                seq = json.getLong("seq")
                apply(base, context, json)
            } catch (t: Throwable) {
                Log.w(TAG, "poll: $t")
                Thread.sleep(1000)
            }
        }
    }

    private fun apply(base: String, context: Context, notice: JSONObject) {
        val seq = notice.getLong("seq")
        val t0 = System.currentTimeMillis()
        val result = JSONObject().put("event", "patch").put("seq", seq).put("publishedAt", notice.optLong("publishedAt"))
        try {
            val specs: JSONArray = notice.getJSONArray("classes")
            val classes = ArrayList<Class<*>>()
            val dexes = ArrayList<ByteArray>()
            val skipped = ArrayList<String>()
            val injected = ArrayList<String>()
            for (i in 0 until specs.length()) {
                val s = specs.getJSONObject(i)
                val name = s.getString("name")
                val dex = get(base + "/patches/" + s.getString("file")) ?: error("download failed: ${s.getString("file")}")
                val cls = try {
                    Class.forName(name, false, context.classLoader)
                } catch (_: ClassNotFoundException) {
                    // A class the installed APK does not have: redefine cannot add it, so it joins the class loader.
                    if (ClassInjector.inject(context, name, dex)) injected += name else skipped += name
                    continue
                }
                classes += cls
                dexes += dex
            }
            val t1 = System.currentTimeMillis()
            val code = JvmtiAgent.redefine(classes.toTypedArray(), dexes.toTypedArray())
            val t2 = System.currentTimeMillis()
            if (code == 0) result.put("recomposeMs", recompose(context))
            result.put("ok", code == 0).put("code", code).put("error", if (code == 0) JSONObject.NULL else JvmtiAgent.errorName(code))
                .put("classes", classes.size).put("skipped", JSONArray(skipped)).put("injected", JSONArray(injected)).put("downloadMs", t1 - t0).put("redefineMs", t2 - t1)
        } catch (t: Throwable) {
            result.put("ok", false).put("error", t.toString())
        }
        result.put("appliedAt", System.currentTimeMillis())
        Log.i(TAG, "patch $seq: $result")
        report(base, result)
    }

    /**
     * Recomposes on the main thread through Compose's HotReloader, the entry Android Studio's Live Edit
     * uses (androidx.compose.runtime 1.12.1: `HotReloaderKt.simulateHotReload(Object)` = saveStateAndDispose +
     * loadStateAndCompose; the argument is ignored). Returns ms until a frame was committed with no recomposer
     * holding pending work, so the screen a caller reads next carries the patch. Two Choreographer frames were
     * not enough: they ran before the frame's draw, and a screenshot 150ms after the report caught the
     * disposed, blank content.
     */
    private fun recompose(context: Context): Long {
        val t0 = System.currentTimeMillis()
        val latch = java.util.concurrent.CountDownLatch(1)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            try {
                Class.forName("androidx.compose.runtime.HotReloaderKt", true, context.classLoader)
                    .getMethod("simulateHotReload", Any::class.java).invoke(null, context)
            } catch (t: Throwable) {
                Log.e(TAG, "hot reload failed", t)
            }
            awaitCommit(main, latch)
        }
        latch.await(RECOMPOSE_CAP_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        return System.currentTimeMillis() - t0
    }

    /**
     * On the main thread: waits for a frame commit drawn with every recomposer idle, which carries the reload,
     * then for the window to stop drawing ([QUIET_MS] with no commit), so animations the reload restarted
     * have finished. Image crossfades and Haze's backdrop blur kept drawing for a few frames after the first
     * idle commit, and a screenshot taken then differed from the next one.
     */
    private fun awaitCommit(main: android.os.Handler, latch: java.util.concurrent.CountDownLatch) {
        if (latch.count == 0L) return
        val views = android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.isShown }
        if (views.isEmpty()) { latch.countDown(); return }
        val idle = androidx.compose.runtime.Recomposer.runningRecomposers.value.none { it.hasPendingWork }
        var fired = false
        val onCommit = Runnable {
            main.post {
                if (fired) return@post
                fired = true
                // Idle when the frame was drawn means it carries the reload; otherwise wait for the next one.
                val done = idle && androidx.compose.runtime.Recomposer.runningRecomposers.value.none { it.hasPendingWork }
                if (done) awaitQuiet(main, latch, views) else awaitCommit(main, latch)
            }
        }
        // A commit callback fires only for a frame that is drawn, so ask for one.
        for (v in views) { v.viewTreeObserver.registerFrameCommitCallback(onCommit); v.invalidate() }
    }

    private fun awaitQuiet(main: android.os.Handler, latch: java.util.concurrent.CountDownLatch, views: List<android.view.View>) {
        if (latch.count == 0L) return
        var drew = false
        val onCommit = Runnable { drew = true }
        for (v in views) v.viewTreeObserver.registerFrameCommitCallback(onCommit)
        main.postDelayed({
            for (v in views) v.viewTreeObserver.unregisterFrameCommitCallback(onCommit)
            if (drew) awaitQuiet(main, latch, views) else latch.countDown()
        }, QUIET_MS)
    }

    private fun get(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 40000
        return try {
            if (c.responseCode == 200) c.inputStream.readBytes() else null
        } finally {
            c.disconnect()
        }
    }

    private fun report(base: String, body: JSONObject) {
        try {
            val c = URL("$base/report").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 5000
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            c.responseCode
            c.disconnect()
        } catch (_: Throwable) {
        }
    }
}
