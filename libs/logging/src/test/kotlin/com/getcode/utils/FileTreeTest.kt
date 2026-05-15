package com.getcode.utils

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FileTreeTest {

    private lateinit var traceDir: File
    private lateinit var fileTree: FileTree

    @BeforeTest
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        traceDir = File(context.filesDir, "traces")
        traceDir.deleteRecursively()

        fileTree = FileTree(context, plugins = { emptyList() })
        Timber.plant(fileTree)
    }

    @AfterTest
    fun teardown() {
        Timber.uprootAll()
    }

    @Test
    fun `log file is null when no logs have been written`() {
        assertNull(fileTree.getLogFile())
    }

    @Test
    fun `writes log lines to file`() {
        Timber.tag("TestTag").d("hello world")

        val file = fileTree.getLogFile()
        assertNotNull(file)
        val content = file.readText()
        assertTrue(content.contains("[D]"))
        assertTrue(content.contains("[TestTag]"))
        assertTrue(content.contains("hello world"))
    }

    @Test
    fun `includes timestamp in log lines`() {
        Timber.i("timestamped")

        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("T"))
        assertTrue(content.contains("[I]"))
    }

    @Test
    fun `appends stack traces for errors`() {
        val error = RuntimeException("boom")
        Timber.tag("Err").e(error, "something failed")

        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("[E]"))
        assertTrue(content.contains("something failed"))
        assertTrue(content.contains("RuntimeException"))
        assertTrue(content.contains("boom"))
    }

    @Test
    fun `multiple log lines are appended`() {
        Timber.d("first")
        Timber.d("second")
        Timber.d("third")

        val content = fileTree.getLogFile()!!.readText()
        val logLines = content.lines().filter { it.startsWith("[") }
        assertEquals(3, logLines.size)
    }

    @Test
    fun `clearLogs removes the log file`() {
        Timber.d("to be cleared")
        assertNotNull(fileTree.getLogFile())

        fileTree.clearLogs()
        assertNull(fileTree.getLogFile())
    }

    @Test
    fun `clears previous session logs on construction`() {
        val context = RuntimeEnvironment.getApplication()
        Timber.d("old session")
        assertNotNull(fileTree.getLogFile())

        // Simulate cold launch — new FileTree clears old logs
        Timber.uproot(fileTree)
        val newTree = FileTree(context, plugins = { emptyList() })
        assertNull(newTree.getLogFile())
    }

    @Test
    fun `applies plugins to log lines`() {
        val context = RuntimeEnvironment.getApplication()
        Timber.uproot(fileTree)

        val uppercasePlugin = TraceLogPlugin { line -> line.uppercase() }
        val tree = FileTree(context, plugins = { listOf(uppercasePlugin) })
        Timber.plant(tree)

        Timber.tag("Tag").d("hello")

        val content = tree.getLogFile()!!.readText()
        assertTrue(content.contains("HELLO"))
        assertTrue(content.contains("[TAG]"))
    }

    @Test
    fun `plugin returning null drops the line`() {
        val context = RuntimeEnvironment.getApplication()
        Timber.uproot(fileTree)

        val dropPlugin = TraceLogPlugin { null }
        val tree = FileTree(context, plugins = { listOf(dropPlugin) })
        Timber.plant(tree)

        Timber.d("should be dropped")

        assertNull(tree.getLogFile())
    }

    @Test
    fun `plugins are applied in order`() {
        val context = RuntimeEnvironment.getApplication()
        Timber.uproot(fileTree)

        val appendA = TraceLogPlugin { "$it-A" }
        val appendB = TraceLogPlugin { "$it-B" }
        val tree = FileTree(context, plugins = { listOf(appendA, appendB) })
        Timber.plant(tree)

        Timber.d("msg")

        val content = tree.getLogFile()!!.readText()
        assertTrue(content.trimEnd().endsWith("-A-B"))
    }

    @Test
    fun `handles null tag gracefully`() {
        Timber.w("no tag")

        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("[W]"))
        assertTrue(content.contains("no tag"))
    }

    // --- Additional coverage ---

    @Test
    fun `writing after clearLogs creates a fresh file`() {
        Timber.d("before clear")
        assertNotNull(fileTree.getLogFile())

        fileTree.clearLogs()
        assertNull(fileTree.getLogFile())

        Timber.d("after clear")
        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("after clear"))
        assertTrue(!content.contains("before clear"))
    }

    @Test
    fun `concurrent writes do not lose lines`() {
        val threadCount = 10
        val linesPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) { threadIdx ->
            executor.submit {
                try {
                    repeat(linesPerThread) { lineIdx ->
                        Timber.d("thread=$threadIdx line=$lineIdx")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val content = fileTree.getLogFile()!!.readText()
        val logLines = content.lines().filter { it.startsWith("[") }
        assertEquals(threadCount * linesPerThread, logLines.size)
    }

    @Test
    fun `maps all priority levels correctly`() {
        Timber.v("verbose")
        Timber.d("debug")
        Timber.i("info")
        Timber.w("warn")
        Timber.e("error")
        Timber.wtf("assert")

        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("[V]"))
        assertTrue(content.contains("[D]"))
        assertTrue(content.contains("[I]"))
        assertTrue(content.contains("[W]"))
        assertTrue(content.contains("[E]"))
        assertTrue(content.contains("[A]"))
    }

    @Test
    fun `plugin that filters selectively only drops matching lines`() {
        val context = RuntimeEnvironment.getApplication()
        Timber.uproot(fileTree)

        val filterPlugin = TraceLogPlugin { line ->
            if (line.contains("SECRET")) null else line
        }
        val tree = FileTree(context, plugins = { listOf(filterPlugin) })
        Timber.plant(tree)

        Timber.d("normal message")
        Timber.d("contains SECRET data")
        Timber.d("another normal message")

        val content = tree.getLogFile()!!.readText()
        val logLines = content.lines().filter { it.startsWith("[") }
        assertEquals(2, logLines.size)
        assertTrue(logLines.all { !it.contains("SECRET") })
    }

    @Test
    fun `log file path is inside traces directory`() {
        Timber.d("path check")
        val file = fileTree.getLogFile()!!
        assertEquals("trace_export.log", file.name)
        assertEquals("traces", file.parentFile?.name)
    }

    @Test
    fun `exported log file includes device header`() {
        Timber.d("header check")
        val content = fileTree.getLogFile()!!.readText()
        assertTrue(content.contains("DEVICE & APP INFO"))
        assertTrue(content.contains("App Version:"))
        assertTrue(content.contains("Device:"))
        assertTrue(content.contains("Android:"))
        assertTrue(content.contains("Exported:"))
    }
}
