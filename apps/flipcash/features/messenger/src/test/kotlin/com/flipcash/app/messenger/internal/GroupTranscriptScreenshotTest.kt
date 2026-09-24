package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.messenger.internal.screens.components.GroupGateBar
import com.flipcash.app.messenger.internal.screens.components.MessageRow
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Renders a group transcript to a PNG in `build/screenshots/` so the run spacing and the avatar
 * gutter can be eyeballed without an emulator. Not an assertion test.
 *
 * Rows are drawn in a plain `Column` rather than the real `LazyColumn`, oldest first, because the
 * transcript's list is `reverseLayout` and index 0 is the newest message. What the rows compute
 * from their neighbours — the gap below, whether they start a run — comes off the paging snapshot
 * either way.
 *
 * Same mechanics as [GroupChromeScreenshotTest]: pump a fixed number of frames and draw the Android
 * view directly, so a composable that keeps scheduling frames can't hang `captureToImage()`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1100dp-xhdpi")
class GroupTranscriptScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noah =
        SenderIdentity(userId = listOf<Byte>(1), displayName = "Noah Parker", picture = null)
    private val chloe =
        SenderIdentity(userId = listOf<Byte>(2), displayName = "Chloe Anderson", picture = null)

    private val start = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun bubble(
        id: Long,
        text: String,
        sender: SenderIdentity?,
        isFromSelf: Boolean = false,
        secondsIn: Long = 0,
    ) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text(text),
        isFromSelf = isFromSelf,
        timestamp = start + secondsIn.seconds,
        sender = sender,
    )

    @Test
    fun rendersGroupTranscript() {
        // Newest first, as the paging snapshot holds them.
        val items = listOf(
            bubble(6, "Fine, Saturday then", sender = null, isFromSelf = true, secondsIn = 50),
            bubble(5, "Watch the tape", chloe, secondsIn = 40),
            bubble(4, "What are you talking about. Did you see him last night?", chloe, secondsIn = 30),
            bubble(3, "Not even close", noah, secondsIn = 20),
            bubble(2, "I disagree. He isn't the best at all", noah, secondsIn = 10),
            ChatListItem.DateSeparator(start),
        )

        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                val messages = flowOf(PagingData.from(items)).collectAsLazyPagingItems()
                Transcript(messages)
            }
        }
        repeat(20) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_transcript.png")
    }

    /**
     * The unread divider between read and unread messages, first on its own and then sharing its
     * gap with a day change, where the date sits above it.
     */
    @Test
    fun rendersUnreadDivider() {
        val alone = listOf(
            bubble(5, "Watch the tape", chloe, secondsIn = 40),
            bubble(4, "What are you talking about. Did you see him last night?", chloe, secondsIn = 30),
            ChatListItem.UnreadDivider(count = 2, date = null),
            bubble(3, "Fine, Saturday then", sender = null, isFromSelf = true, secondsIn = 20),
            bubble(2, "I disagree. He isn't the best at all", noah, secondsIn = 10),
            ChatListItem.DateSeparator(start),
        )
        val withDate = listOf(
            bubble(5, "Watch the tape", chloe, secondsIn = 90_000),
            ChatListItem.UnreadDivider(count = 1, date = start + 90_000.seconds),
            bubble(3, "Fine, Saturday then", sender = null, isFromSelf = true, secondsIn = 20),
            ChatListItem.DateSeparator(start),
        )

        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Transcript(flowOf(PagingData.from(alone)).collectAsLazyPagingItems())
                    Transcript(flowOf(PagingData.from(withDate)).collectAsLazyPagingItems())
                }
            }
        }
        repeat(20) { composeRule.mainClock.advanceTimeByFrame() }

        capture("unread_divider.png")
    }

    /**
     * The same transcript seen from outside the group, once per access: an eligible viewer reads it
     * sharp over the Join gate, while a blocked one and one whose balance has not arrived yet see it
     * blurred. The blur and the gate read the state's own flags, so this is the split as the screen
     * draws it rather than a restatement of it.
     *
     * A heavy fade stands in for `BlurredContent`. Robolectric's native graphics draws neither
     * blur: Haze's runtime shader does not compile there, and `Modifier.blur` renders sharp.
     */
    @Test
    fun rendersTranscriptFromOutsideTheGroup() {
        val items = listOf(
            bubble(3, "Not even close", noah, secondsIn = 20),
            bubble(2, "I disagree. He isn't the best at all", noah, secondsIn = 10),
            ChatListItem.DateSeparator(start),
        )
        val requirement = ChatRuleRequirement.MinimumBalance(mints = emptyList(), amount = Fiat(100.0))
        val outsider = ChatSubject.Group(
            chatId = ChatId(byteArrayOf(1)),
            groupTitle = "Bad Boys",
            picture = null,
            memberCount = 412L,
            rules = ChatRules(listener = listOf(requirement), speaker = emptyList()),
            isMember = false,
        )
        val states = listOf(
            ChatViewModel.State(subject = outsider, groupAccess = GroupAccess.Eligible),
            ChatViewModel.State(subject = outsider, groupAccess = GroupAccess.Blocked(requirement)),
            ChatViewModel.State(subject = outsider, groupAccess = null),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                val messages = flowOf(PagingData.from(items)).collectAsLazyPagingItems()
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    states.forEach { state ->
                        Column(modifier = Modifier.width(360.dp)) {
                            Box(
                                modifier = Modifier.alpha(if (state.obscuresTranscript) 0.15f else 1f),
                            ) {
                                Transcript(messages)
                            }
                            if (state.replacesComposer) {
                                GroupGateBar(
                                    access = state.groupAccess,
                                    requirement = requirement,
                                    staffOnly = false,
                                    currency = null,
                                    onAction = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        repeat(20) { composeRule.mainClock.advanceTimeByFrame() }

        capture("group_transcript_from_outside.png")
    }

    @Composable
    private fun Transcript(messages: LazyPagingItems<ChatListItem>) {
        Column(modifier = Modifier.width(360.dp).padding(16.dp)) {
            // Oldest first: the list this stands in for is reverseLayout.
            for (index in messages.itemCount - 1 downTo 0) {
                val item = messages[index] ?: continue
                MessageRow(
                    index = index,
                    item = item,
                    messages = messages,
                    separatorConfig = SeparatorConfig.DayOnly,
                    otherReadPointer = null,
                    selecting = false,
                    focused = true,
                    animateInsertion = false,
                    showsSenderGutter = true,
                )
            }
        }
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

    /** Crop away the untouched (fully transparent) margin, so the PNG is just what was composed. */
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
