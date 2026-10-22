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
        try {
            JvmtiAgent.attach(context)
        } catch (t: Throwable) {
            Log.e(TAG, "agent attach failed", t)
            report(base, JSONObject().put("event", "attach").put("ok", false).put("error", t.toString()))
            return
        }
        report(base, JSONObject().put("event", "attach").put("ok", true))
        // Start from the server's current patch, so a restart does not replay an old one.
        var seq = -1L
        while (seq < 0) {
            seq = try { JSONObject(String(get("$base/seq") ?: continue)).getLong("seq") } catch (_: Throwable) { Thread.sleep(1000); -1L }
        }
        while (true) {
            try {
                val notice = get(base + "/next?after=$seq") ?: continue
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
            for (i in 0 until specs.length()) {
                val s = specs.getJSONObject(i)
                val cls = try {
                    Class.forName(s.getString("name"), false, context.classLoader)
                } catch (_: ClassNotFoundException) {
                    skipped += s.getString("name") // a new class (e.g. a lambda); redefine cannot add it
                    continue
                }
                classes += cls
                dexes += get(base + "/patches/" + s.getString("file")) ?: error("download failed: ${s.getString("file")}")
            }
            val t1 = System.currentTimeMillis()
            val code = JvmtiAgent.redefine(classes.toTypedArray(), dexes.toTypedArray())
            val t2 = System.currentTimeMillis()
            result.put("ok", code == 0).put("code", code).put("error", if (code == 0) JSONObject.NULL else JvmtiAgent.errorName(code))
                .put("classes", classes.size).put("skipped", JSONArray(skipped)).put("downloadMs", t1 - t0).put("redefineMs", t2 - t1)
        } catch (t: Throwable) {
            result.put("ok", false).put("error", t.toString())
        }
        result.put("appliedAt", System.currentTimeMillis())
        Log.i(TAG, "patch $seq: $result")
        report(base, result)
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
