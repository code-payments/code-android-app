package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.Router
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.DetectedUrl
import com.getcode.solana.keys.Mint
import dev.theolm.rinku.DeepLink
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Card half of `test-vectors/link_detection.json`. Synced copy — a failure is fixed in the
 * canonical fixture and re-synced to both platforms, never edited here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkCardClassifierTest {

    /**
     * The real `AppRouter` needs an auth state and a current user; classification needs neither.
     * This stands in for the parser only, with the same path rules the real one applies to the
     * routes that can become cards — including its internal jump unwrap, so that a classifier
     * that failed to unwrap first would still reach `CashLink` here and the host gate it skipped
     * would go unnoticed.
     */
    private val router = object : Router {
        override fun classify(deepLink: DeepLink): DeeplinkType? {
            val link = deepLink.unwrapJumpForTest() ?: deepLink
            val path = link.pathSegmentsForTest().firstOrNull()
            return when (path) {
                "c", "cash" -> DeeplinkType.CashLink(link.entropyFragmentForTest().orEmpty())
                "token" -> link.pathSegmentsForTest().getOrNull(1)
                    ?.let { DeeplinkType.TokenInfo(Mint(it)) }
                else -> null
            }
        }

        override fun dispatch(deepLink: DeepLink) = error("not used in classification tests")
    }

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!
            .getResourceAsStream("link_detection.json")!!
            .bufferedReader().use { it.readText() }
    )

    private fun vectors(): List<JSONObject> {
        val array = fixture().getJSONArray("vectors")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `card eligibility matches the cross-platform vectors`() {
        val classifier = LinkCardClassifier(router)

        for (vector in vectors()) {
            val name = vector.getString("name")
            val spansArray = vector.getJSONArray("spans")
            val links = (0 until spansArray.length()).map {
                val span = spansArray.getJSONObject(it)
                DetectedUrl(
                    start = span.getInt("start"),
                    end = span.getInt("end"),
                    url = span.getString("url"),
                )
            }

            val actual = classifier.firstCard(links)

            val expectedCard = vector.optJSONObject("card")
            if (expectedCard == null) {
                assertEquals(null, actual, "vector `$name`: ${vector.getString("note")}")
            } else {
                val note = vector.getString("note")
                when (val kind = expectedCard.getString("kind")) {
                    "cash" -> {
                        val card = actual as? LinkCard.Cash
                        assertEquals(expectedCard.getString("url"), card?.url, "vector `$name`: $note")
                        assertEquals(
                            LinkCard.Cash.State.Unresolved,
                            card?.state,
                            "vector `$name` must start unresolved",
                        )
                    }

                    "token" -> {
                        val card = actual as? LinkCard.TokenInfo
                        assertEquals(expectedCard.getString("url"), card?.url, "vector `$name`: $note")
                        assertEquals(
                            expectedCard.getString("mint"),
                            card?.mint?.description,
                            "vector `$name` must carry the mint from the path",
                        )
                        assertEquals(
                            LinkCard.TokenInfo.State.Unresolved,
                            card?.state,
                            "vector `$name` must start unresolved",
                        )
                    }

                    else -> error("vector `$name` has an unknown card kind `$kind`")
                }
                // The card carries one of the spans the transcript would have underlined, which is
                // what lets the bubble draw it in place of exactly that text. Its own url can
                // differ from the span's -- a jump wrapper is unwrapped -- so the span is the link.
                assertTrue(
                    links.any { it.start == actual?.start && it.end == actual.end },
                    "vector `$name` must carry the span its link was detected at",
                )
            }
        }
    }

    @Test
    fun `the host allowlist matches the cross-platform fixture`() {
        val hosts = fixture().getJSONArray("cardHosts")
        val expected = (0 until hosts.length()).map { hosts.getString(it) }.toSet()

        assertEquals(expected, LinkCardClassifier.CARD_HOSTS)
    }

    /**
     * Not yet a fixture vector — raise it against the canonical fixture so iOS is held to it too.
     *
     * The redirector host is card-eligible, but what it redirects *to* is attacker-chosen. The
     * host gate has to land on the unwrapped target, because `AppRouter` unwraps internally and
     * classifies by path alone: gate the wrapper instead and `evil.com/c/#/e=…` comes back as a
     * cash link wearing Flipcash branding.
     */
    @Test
    fun `a jump wrapper pointing off-host is not a card`() {
        val classifier = LinkCardClassifier(router)

        val hostile = "https://jump.flipcash.com/#source=" +
            "https%3A%2F%2Fevil.com%2Fc%2F%23%2Fe%3DKNi8pQr1n5hRU65vKJGge3"

        assertNull(classifier.firstCard(listOf(DetectedUrl(0, hostile.length, hostile))))
    }

    /** A jump wrapper is unwrapped once. One pointing at another jump is malformed, not a card. */
    @Test
    fun `a jump wrapper pointing at another jump is not a card`() {
        val classifier = LinkCardClassifier(router)

        val nested = "https://jump.flipcash.com/#source=" +
            "https%3A%2F%2Fjump.flipcash.com%2F%23source%3Dhttps%253A%252F%252Fsend.flipcash.com%252Fc%252F%2523%252Fe%253DKNi8pQr1n5hRU65vKJGge3"

        assertNull(classifier.firstCard(listOf(DetectedUrl(0, nested.length, nested))))
    }
}

private fun DeepLink.uriForTest() = android.net.Uri.parse(data)

private fun DeepLink.pathSegmentsForTest(): List<String> =
    uriForTest().pathSegments.orEmpty()

private fun DeepLink.entropyFragmentForTest(): String? =
    uriForTest().encodedFragment
        ?.split("/")
        ?.firstOrNull { it.startsWith("e=") }
        ?.removePrefix("e=")

private fun DeepLink.unwrapJumpForTest(): DeepLink? {
    val uri = uriForTest()
    if (uri.host != "jump.flipcash.com") return null
    val source = uri.encodedFragment?.removePrefix("source=") ?: return null
    return DeepLink(android.net.Uri.decode(source))
}
