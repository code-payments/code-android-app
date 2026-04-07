package com.getcode.utils

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TraceManagerTest {

    @BeforeTest
    fun setup() {
        TraceManager.reset()
        Timber.uprootAll()
    }

    @AfterTest
    fun teardown() {
        TraceManager.reset()
        Timber.uprootAll()
    }

    @Test
    fun `initialize plants FileTree and registers PiiMaskingPlugin`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)

        assertTrue(TraceManager.plugins.any { it is PiiMaskingPlugin })
        // FileTree should be planted — logging should produce a file
        Timber.d("init test")
        assertNotNull(TraceManager.getLogFile())
    }

    @Test
    fun `initialize is idempotent`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)
        val pluginCountAfterFirst = TraceManager.plugins.size
        val treeCountAfterFirst = Timber.forest().size

        TraceManager.initialize(context)
        assertEquals(pluginCountAfterFirst, TraceManager.plugins.size)
        assertEquals(treeCountAfterFirst, Timber.forest().size)
    }

    @Test
    fun `addPlugin appends to plugin list`() {
        val plugin = TraceLogPlugin { it }
        TraceManager.addPlugin(plugin)

        assertTrue(TraceManager.plugins.contains(plugin))
    }

    @Test
    fun `removePlugin removes from plugin list`() {
        val plugin = TraceLogPlugin { it }
        TraceManager.addPlugin(plugin)
        assertTrue(TraceManager.plugins.contains(plugin))

        TraceManager.removePlugin(plugin)
        assertTrue(!TraceManager.plugins.contains(plugin))
    }

    @Test
    fun `getLogFile returns null before initialize`() {
        assertNull(TraceManager.getLogFile())
    }

    @Test
    fun `getLogFile delegates to FileTree after initialize`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)

        // No logs yet
        assertNull(TraceManager.getLogFile())

        Timber.d("test")
        assertNotNull(TraceManager.getLogFile())
    }

    @Test
    fun `clearLogs delegates to FileTree`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)
        Timber.d("to clear")
        assertNotNull(TraceManager.getLogFile())

        TraceManager.clearLogs()
        assertNull(TraceManager.getLogFile())
    }

    @Test
    fun `clearLogs is safe before initialize`() {
        // Should not throw
        TraceManager.clearLogs()
    }

    @Test
    fun `plugins added before initialize are applied after initialize`() {
        val context = RuntimeEnvironment.getApplication()
        val marker = TraceLogPlugin { "$it [MARKED]" }
        TraceManager.addPlugin(marker)

        TraceManager.initialize(context)
        Timber.d("check marker")

        val content = TraceManager.getLogFile()!!.readText()
        assertTrue(content.contains("[MARKED]"))
    }

    @Test
    fun `reset clears plugins and file tree`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)
        Timber.d("before reset")
        assertNotNull(TraceManager.getLogFile())
        assertTrue(TraceManager.plugins.isNotEmpty())

        TraceManager.reset()
        assertTrue(TraceManager.plugins.isEmpty())
        assertNull(TraceManager.getLogFile())
    }

    @Test
    fun `can reinitialize after reset`() {
        val context = RuntimeEnvironment.getApplication()
        TraceManager.initialize(context)
        TraceManager.reset()

        TraceManager.initialize(context)
        Timber.d("after reinit")
        assertNotNull(TraceManager.getLogFile())
        assertTrue(TraceManager.plugins.any { it is PiiMaskingPlugin })
    }
}
