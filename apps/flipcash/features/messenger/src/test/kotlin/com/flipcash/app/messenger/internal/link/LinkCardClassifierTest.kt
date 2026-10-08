package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.Router
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.DetectedUrl
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.isUsernameShaped
import com.getcode.opencode.model.core.bytes
import com.getcode.solana.keys.Mint
import dev.theolm.rinku.DeepLink
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
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
                // As `AppRouter` does: the id segment is checked, anything after it is not.
                "chat" -> link.pathSegmentsForTest().getOrNull(1)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let { DeeplinkType.GroupChatInvite(ChatId(it.bytes)) }
                "tip" -> link.pathSegmentsForTest().getOrNull(1)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let { DeeplinkType.Tipcard(it.bytes) }
                else -> link.profileLinkForTest()
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
                            LinkCard.Cash.State.Loading,
                            card?.state,
                            "vector `$name` must start with its lookup still to do",
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
                            LinkCard.TokenInfo.State.Loading,
                            card?.state,
                            "vector `$name` must start with its lookup still to do",
                        )
                    }

                    "group" -> {
                        val card = actual as? LinkCard.GroupInvite
                        assertEquals(expectedCard.getString("url"), card?.url, "vector `$name`: $note")
                        assertEquals(
                            LinkCard.GroupInvite.State.Loading,
                            card?.state,
                            "vector `$name` must start with its lookup still to do",
                        )
                    }

                    "user" -> {
                        val card = actual as? LinkCard.User
                        assertEquals(expectedCard.getString("url"), card?.url, "vector `$name`: $note")
                        assertEquals(
                            LinkCard.User.State.Loading,
                            card?.state,
                            "vector `$name` must start with its lookup still to do",
                        )
                    }

                    // LinkCard.Web does not exist yet; compare the url only.
                    "web" -> assertEquals(expectedCard.getString("url"), actual?.url, "vector `$name`: $note")

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

    /** Not in the canonical fixture yet; iOS holds the same case in its own classifier tests. */
    @Test
    fun `a group invite link becomes a group card`() {
        val classifier = LinkCardClassifier(router)
        val url = "https://app.flipcash.com/chat/6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162"

        val card = classifier.firstCard(listOf(DetectedUrl(0, url.length, url)))

        val group = card as? LinkCard.GroupInvite
        assertEquals(
            ChatId(UUID.fromString("6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162").bytes),
            group?.chatId,
        )
        assertEquals(url, group?.url)
        assertEquals(0, group?.start)
        assertEquals(url.length, group?.end)
        assertEquals(LinkCard.GroupInvite.State.Loading, group?.state)
    }

    /** The send-cash path under a chat opens a payment, not the group, so it stays a plain link. */
    @Test
    fun `a chat send cash link stays a link`() {
        val classifier = LinkCardClassifier(router)
        val url = "https://app.flipcash.com/chat/6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162/send"

        assertNull(classifier.firstCard(listOf(DetectedUrl(0, url.length, url))))
    }

    // Person cards

    private fun cardFor(url: String): LinkCard? =
        LinkCardClassifier(router).firstCard(listOf(DetectedUrl(0, url.length, url)))

    private val userId = UUID.fromString("2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b").bytes

    @Test
    fun `a handle link becomes a person card`() {
        val user = cardFor("https://flipcash.com/satoshi") as? LinkCard.User

        assertEquals(LinkCard.User.Identity.ByUsername("satoshi"), user?.identity)
        assertEquals("@satoshi", user?.linkedHandle)
        assertEquals(LinkCard.User.State.Loading, user?.state)
    }

    @Test
    fun `an id link becomes a person card`() {
        listOf(
            "https://flipcash.com/2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b",
            "https://flipcash.com/tip/2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b",
        ).forEach { url ->
            val user = cardFor(url) as? LinkCard.User
            assertEquals(LinkCard.User.Identity.ById(userId), user?.identity, url)
            assertNull(user?.linkedHandle, url)
        }
    }

    /**
     * The router reads any single segment as a handle, so only the host gate stops these from
     * becoming person cards; they fall through to the outside-link card.
     */
    @Test
    fun `a person shaped link on another host stays a link`() {
        listOf(
            "https://discord.gg/x",
            "https://t.me/satoshi",
            "https://example.com/2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b",
            "https://example.com/tip/2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b",
        ).forEach { assertTrue(cardFor(it) is LinkCard.Web, it) }
    }

    /** HttpUrl would decode the escape and fetch `example.com`; iOS refuses it (parity decision D11). */
    @Test
    fun `a percent escaped host gets no web card`() {
        assertNull(cardFor("https://ex%61mple.com/"))
    }

    /** iOS gives an explicit port other than 443 no card (parity decision D12). */
    @Test
    fun `a port other than 443 gets no web card`() {
        assertNull(cardFor("https://example.com:6379/"))
        assertNull(cardFor("https://example.com:8443/a"))
        assertTrue(cardFor("https://example.com:443/") is LinkCard.Web)
        assertTrue(cardFor("https://example.com/") is LinkCard.Web)
    }

    @Test
    fun `a website page stays a link`() {
        listOf(
            "https://flipcash.com/download",
            "https://flipcash.com/Privacy",
            "https://flipcash.com/terms",
            "https://flipcash.com/currencycreator",
            "https://flipcash.com/api",
        ).forEach { assertNull(cardFor(it), it) }
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

/**
 * `AppRouter`'s bare-host person link. Its reserved list is `internal` to the router module, so a
 * copy stands in here; `AppRouterTest` holds the real list to the website's pages.
 */
private fun DeepLink.profileLinkForTest(): DeeplinkType? {
    val uri = uriForTest()
    if (uri.host?.removePrefix("www.") != "flipcash.com") return null
    val segment = uri.pathSegments.singleOrNull()?.lowercase() ?: return null
    if (Regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$").matches(segment)) {
        return DeeplinkType.Profile(ProfileAddress.ById(UUID.fromString(segment).bytes))
    }
    return if (segment.isUsernameShaped() && segment !in reservedProfilePathsForTest) {
        DeeplinkType.Profile(ProfileAddress.ByUsername(segment))
    } else {
        null
    }
}

private val reservedProfilePathsForTest = setOf(
    "download", "privacy", "terms", "support", "help", "about", "blog", "legal", "currencycreator",
    "app", "api", "assets", "fonts", "icons", "js", "v1", "pool", "wallet",
    "login", "c", "cash", "verify", "token", "chat", "tip",
)

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
