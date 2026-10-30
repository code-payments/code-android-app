package com.flipcash.desktop

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.flipcash.app.startup.contract.StartupContract
import com.flipcash.app.startup.contract.StartupContract.Desktop
import com.flipcash.app.startup.contract.StartupContract.Step
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.events.Events
import com.hoc081098.channeleventbus.ChannelEventBusException
import com.hoc081098.channeleventbus.ValidationBeforeClosing
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Builds the real Metro graph and checks it against [StartupContract]: the session listeners
 * desktop must bind, the startup steps it must repeat, and that singletons stay single. Each check
 * here stands for a gap that once left the desktop wallet on its loading spinner with no error.
 */
class DesktopWiringTest {
    private val graph = createGraph<DesktopGraph>()

    @Test
    fun `binds every shared session listener and nothing unclassified`() {
        val bound = graph.sessionListeners.map { it::class.qualifiedName }.toSet()
        val missing = StartupContract.sharedSessionListeners - bound
        assertTrue(missing.isEmpty(), "desktop graph does not bind these SessionListeners: $missing")
        val unclassified = bound - StartupContract.sharedSessionListeners -
            StartupContract.androidOnlySessionListeners.keys
        assertTrue(unclassified.isEmpty(), "SessionListeners missing from StartupContract: $unclassified")
    }

    @Test
    fun `startup takes effect for every mirrored step`() = runBlocking {
        graph.startup()
        val steps = StartupContract.androidStartup.values.filterIsInstance<Desktop.Mirrored>().map { it.step }
        assertEquals(Step.entries.toSet(), steps.toSet(), "a Step no Android entry point maps to")
        for (step in Step.entries) {
            // Exhaustive, so a new Step does not compile until it has a check here.
            when (step) {
                Step.EventRepository -> assertEventRepositoryListening()
                Step.CurveTables -> assertTrue(
                    Estimator.reserveAtSupply(BigDecimal.ONE).isSuccess,
                    "curve tables not loaded: ${Estimator.reserveAtSupply(BigDecimal.ONE).exceptionOrNull()}",
                )
                Step.NetworkImageLoader -> {
                    val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
                    // coil-network-okhttp registers its fetcher through ServiceLoader, so this
                    // fails when the artifact is off the classpath, which is what blanked the logos.
                    val factories = loader.components.fetcherFactories.map { it.first::class.qualifiedName }
                    assertTrue(
                        factories.any { it?.startsWith("coil3.network.") == true },
                        "image loader has no network fetcher: $factories",
                    )
                    // The disk cache comes only from installImageLoader, so this fails when Coil
                    // built its default loader instead.
                    assertTrue(
                        loader.diskCache?.directory?.name == "image_cache",
                        "Coil is using its default loader, not installImageLoader's",
                    )
                }
            }
        }
    }

    @Test
    fun `scoped bindings resolve to one instance`() {
        // Metro once dropped provider-level @Singleton read through Dagger factories, which gave
        // every consumer its own event bus; login events then reached nobody. Only bindings scoped
        // on Android belong here: AccountController, for one, is unscoped there too.
        assertSame(graph.eventBus, graph.eventBus, "ChannelEventBus")
        assertSame(graph.userManager, graph.userManager, "UserManager")
        assertSame(graph.exchange, graph.exchange, "Exchange")
        assertSame(graph.eventRepository, graph.eventRepository, "EventRepository")
        assertSame(graph.tokenCoordinator, graph.tokenCoordinator, "TokenCoordinator")
        val inSet = graph.sessionListeners.single { it::class == graph.tokenCoordinator::class }
        assertSame(graph.tokenCoordinator, inSet, "TokenCoordinator in the listener set is a second instance")
    }

    /**
     * The repository subscribes to `Events.OnLoggedIn` on the shared bus. Closing that key with
     * REQUIRE_FLOW_IS_NOT_COLLECTING fails only while someone collects it; the subscription is
     * launched on IO, so give it a moment.
     */
    private suspend fun assertEventRepositoryListening() {
        val collecting = withTimeoutOrNull(5_000) {
            while (true) {
                try {
                    graph.eventBus.closeKey(
                        Events.OnLoggedIn,
                        setOf(
                            ValidationBeforeClosing.REQUIRE_BUS_IS_EXISTING,
                            ValidationBeforeClosing.REQUIRE_FLOW_IS_NOT_COLLECTING,
                        ),
                    )
                    fail("Events.OnLoggedIn has a bus but no collector")
                } catch (_: ChannelEventBusException.CloseException.BusIsCollecting) {
                    return@withTimeoutOrNull true
                } catch (_: ChannelEventBusException.CloseException.BusDoesNotExist) {
                    delay(50)
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        assertEquals(true, collecting, "nothing collects Events.OnLoggedIn: the event repository was never created")
    }
}
