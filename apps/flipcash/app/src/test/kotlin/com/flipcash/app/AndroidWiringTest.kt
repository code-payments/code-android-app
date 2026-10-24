package com.flipcash.app

import coil3.SingletonImageLoader
import com.flipcash.app.inject.ApiModule
import com.flipcash.app.startup.contract.StartupContract
import com.getcode.opencode.providers.SessionListener
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals

/**
 * Checks the Android app's wiring against [StartupContract], so a session listener or startup entry
 * point added here has to be classified there before the build passes, and the desktop graph's own
 * test then holds desktop to it.
 *
 * `HiltTestApplication` replaces [FlipcashApp], so this does not run Android's startup; it checks
 * the listener set the real Hilt graph builds and that every startup entry point is accounted for.
 *
 * [ApiModule] is replaced because `MixpanelAPI.getInstance` returns null under Robolectric; it has no
 * part in session listeners or startup.
 */
@HiltAndroidTest
@UninstallModules(ApiModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = HiltTestApplication::class, sdk = [35])
class AndroidWiringTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    val mixpanel: MixpanelAPI = mockk(relaxed = true)

    @Inject
    lateinit var sessionListeners: Set<@JvmSuppressWildcards SessionListener>

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `session listeners are exactly the classified set`() {
        val bound = sessionListeners.map { it::class.java.name }.toSet()
        val classified = StartupContract.sharedSessionListeners + StartupContract.androidOnlySessionListeners.keys
        assertEquals(classified, bound, "classify each SessionListener in StartupContract")
    }

    @Test
    fun `every startup entry point is classified`() {
        assertEquals(
            StartupContract.androidStartup.keys.sorted(),
            (manifestInitializers() + appInjections() + appImageLoader()).sorted(),
            "classify each startup entry point in StartupContract.androidStartup",
        )
    }

    /** androidx-startup initializers this module's manifest adds (not the ones it removes). */
    private fun manifestInitializers(): List<String> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val metaData = doc.getElementsByTagName("meta-data")
        return (0 until metaData.length).map { metaData.item(it) as Element }
            .filter { it.getAttributeNS(ANDROID, "value") == "androidx.startup" }
            .filter { it.getAttributeNS(TOOLS, "node") != "remove" }
            .map { it.getAttributeNS(ANDROID, "name") }
    }

    /** What [FlipcashApp] injects, unwrapping `Lazy<T>` to `T`. */
    private fun appInjections(): List<String> =
        FlipcashApp::class.java.declaredFields
            .filter { it.isAnnotationPresent(Inject::class.java) }
            .map { field ->
                val type = field.genericType
                if (type is ParameterizedType && type.rawType == dagger.Lazy::class.java) {
                    (type.actualTypeArguments.single() as Class<*>).name
                } else {
                    field.type.name
                }
            }

    private fun appImageLoader(): List<String> =
        listOfNotNull(
            SingletonImageLoader.Factory::class.java.name
                .takeIf { SingletonImageLoader.Factory::class.java.isAssignableFrom(FlipcashApp::class.java) },
        )

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"
    }
}
