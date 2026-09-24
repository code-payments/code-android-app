package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.ChatInfoCard
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.GroupGateBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.ChatType
import com.getcode.navigation.core.CodeNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.view.LoadingSuccessState
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the group chrome — title bar, info card, gate bar — to PNGs in `build/screenshots/` so
 * the layout can be eyeballed without an emulator. Not an assertion test.
 *
 * Same mechanics as [ChatIdentityScreenshotTest]: pause the clock, pump a fixed number of frames,
 * and draw the Android view directly, so a composable that keeps scheduling frames can't hang
 * `captureToImage()`'s implicit `waitForIdle`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1100dp-xhdpi")
class GroupChromeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mint = Mint(List(32) { 7.toByte() })

    private val balanceRule = ChatRules(
        listener = listOf(
            ChatRuleRequirement.MinimumBalance(
                mints = listOf(mint),
                amount = Fiat(100.0),
            ),
        ),
        speaker = emptyList(),
    )

    // The member's view: no rules line worth gating on, a real member count.
    private val memberedGroup = ChatSubject.Group(
        chatId = ChatId(byteArrayOf(1)),
        groupTitle = "Bad Boys",
        picture = null,
        memberCount = 412L,
        rules = null,
        isMember = true,
    )

    // The outsider's view: same group, carrying the balance rule the gate reports.
    private val gatedGroup = memberedGroup.copy(rules = balanceRule, isMember = false)

    // Staff-only, with no balance to state: the card's staff line is the only rule it shows.
    private val staffGroup = memberedGroup.copy(
        rules = ChatRules(listener = listOf(ChatRuleRequirement.Staff), speaker = emptyList()),
        isMember = false,
    )

    // Both rules on one chat, which the proto allows and the card states as two lines.
    private val bothRules = ChatRules(
        listener = listOf(
            ChatRuleRequirement.MinimumBalance(mints = listOf(mint), amount = Fiat(100.0)),
            ChatRuleRequirement.Staff,
        ),
        speaker = emptyList(),
    )

    // The degenerate roster: the plural string's "1 person" arm and an untitled group.
    private val soloGroup = memberedGroup.copy(groupTitle = null, memberCount = 1)

    @Test
    fun rendersGroupTitleBar() {
        val navigator = mockk<CodeNavigator>(relaxed = true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    listOf(memberedGroup, gatedGroup, soloGroup).forEach { group ->
                        ChatTopBar(
                            navigator = navigator,
                            state = ChatViewModel.State(
                                subject = group,
                                chatType = ChatType.GROUP,
                            ),
                            onBarHeightChange = {},
                            chatActionHandler = {},
                            dispatch = {},
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_top_bar.png")
    }

    @Test
    fun rendersGroupInfoCard() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val cardWidth = Modifier.width(300.dp)
                    ChatInfoCard(subject = memberedGroup, modifier = cardWidth)
                    // The currency resolved, as it is once the token cache fills in.
                    ChatInfoCard(
                        subject = gatedGroup,
                        modifier = cardWidth,
                        currencyName = "Bad Boys",
                    )
                    // The currency not yet resolved — the frame the card renders first.
                    ChatInfoCard(subject = gatedGroup, modifier = cardWidth)
                    // A staff-only group, and one asking for both: the card states each rule it has.
                    ChatInfoCard(subject = staffGroup, modifier = cardWidth)
                    ChatInfoCard(
                        subject = staffGroup.copy(rules = bothRules),
                        modifier = cardWidth,
                        currencyName = "Bad Boys",
                    )
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_info_card.png")
    }

    @Test
    fun rendersGroupGateBar() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val balanceRule = ChatRuleRequirement.MinimumBalance(
                        mints = listOf(mint),
                        amount = Fiat(100.0),
                    )
                    val badBoys = RuleCurrency(name = "Bad Boys", isReserve = false)
                    // Blocked on a balance, currency resolved: "Buy More Bad Boys", enabled.
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = balanceRule),
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = false,
                        currency = badBoys,
                    )
                    // The same state before the currency resolves: "Buy More", still enabled — the
                    // mint is the rule's, so the button buys the right thing before it can name it.
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = balanceRule),
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = false,
                        currency = null,
                    )
                    // Any holding counts: no token to buy, so "Add Cash", and the caption states
                    // the amount alone.
                    val anyMintRule = ChatRuleRequirement.MinimumBalance(
                        mints = emptyList(),
                        amount = Fiat(100.0),
                    )
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = anyMintRule),
                        requirement = anyMintRule,
                        onAction = {},
                        staffOnly = false,
                        currency = null,
                    )
                    // Gated on the reserve: the caption drops "of Dollars" because the amount is
                    // already a dollar figure, and the button adds cash rather than buying.
                    val reserveRule = ChatRuleRequirement.MinimumBalance(
                        mints = listOf(Mint.usdf),
                        amount = Fiat(100.0),
                    )
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = reserveRule),
                        requirement = reserveRule,
                        onAction = {},
                        staffOnly = false,
                        currency = RuleCurrency(name = "Dollars", isReserve = true),
                    )
                    // Blocked on staff: no action the viewer can take, and no balance to state —
                    // but the line says why the button is dead rather than leaving it bare.
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = ChatRuleRequirement.Staff),
                        requirement = null,
                        onAction = {},
                        staffOnly = true,
                        currency = null,
                    )
                    // Eligible for a gated group — node 10127:117120. The requirement is stated over
                    // a Join that works: it is the group's rule, not this viewer's shortfall.
                    GroupGateBar(
                        access = GroupAccess.Eligible,
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = false,
                        currency = badBoys,
                    )
                    // Mid-join: the roster has not confirmed membership yet.
                    GroupGateBar(
                        access = GroupAccess.Eligible,
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = false,
                        currency = badBoys,
                        joinProgress = LoadingSuccessState(loading = true),
                    )
                    // The join came back accepted: the checkmark the gate holds over the (already
                    // sharp) transcript, before the composer takes its place.
                    GroupGateBar(
                        access = GroupAccess.Membered,
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = false,
                        currency = badBoys,
                        joinProgress = LoadingSuccessState(success = true),
                    )
                    // A group with no rules at all: nothing to state, one button.
                    GroupGateBar(
                        access = GroupAccess.Eligible,
                        requirement = null,
                        onAction = {},
                        staffOnly = false,
                        currency = null,
                    )
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_gate_bar.png")
    }

    @Test
    fun rendersStaffGateBar() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val balanceRule = ChatRuleRequirement.MinimumBalance(
                        mints = listOf(mint),
                        amount = Fiat(100.0),
                    )
                    // Staff, in a staff-only group they are not in: the same line over a Join that
                    // works. This is the case that used to arrive as a disabled button, because the
                    // gate treated the staff rule as unsatisfiable by anyone.
                    GroupGateBar(
                        access = GroupAccess.Eligible,
                        requirement = null,
                        onAction = {},
                        staffOnly = true,
                        currency = null,
                    )
                    // A group asking for both: two lines, one per rule.
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = balanceRule),
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = true,
                        currency = RuleCurrency(name = "Bad Boys", isReserve = false),
                    )
                    // Staff who clear the staff rule but not the balance one: the balance line is
                    // still what is holding them up, so Buy More is what they get.
                    GroupGateBar(
                        access = GroupAccess.Blocked(unmet = balanceRule),
                        requirement = balanceRule,
                        onAction = {},
                        staffOnly = true,
                        currency = RuleCurrency(name = "Dollars", isReserve = true),
                    )
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_gate_bar_staff.png")
    }

    private fun capture(name: String) {
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val cropped = bitmap.trimmedToDrawnArea()

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${cropped.width}x${cropped.height})")
    }

    /**
     * The content view is the full device, but the previews wrap their content — crop away the
     * untouched (fully transparent) margin so the PNG is just what was composed.
     */
    private fun Bitmap.trimmedToDrawnArea(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return this
        return Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
    }
}
