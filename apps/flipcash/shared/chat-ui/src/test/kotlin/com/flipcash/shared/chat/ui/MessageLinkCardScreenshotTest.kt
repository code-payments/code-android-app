package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCard.Message.Body
import com.flipcash.shared.chat.models.LinkCard.Message.Excerpt
import com.flipcash.shared.chat.models.LinkCard.Message.Kind
import com.flipcash.shared.chat.models.LinkCard.Message.Source
import com.flipcash.shared.chat.models.LinkCard.Message.State
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders every message card state to `build/screenshots/message_link_card.png`, to set beside the
 * card component set (Figma node 11396:118). Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w402dp-h3200dp-xhdpi")
class MessageLinkCardScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chatId = ChatId(List(16) { it.toByte() })
    private val sentAt = Instant.fromEpochSeconds(1_760_000_000)

    private fun source(kind: Kind, title: String = "Moony") =
        Source(title = title, kind = kind, picture = null, pictureAccess = BlobAccessContext.ChatProfile(chatId))

    private fun excerpt(body: Body, author: String = "Ada") =
        Excerpt(author = author, isFromSelf = false, sentAt = sentAt, body = body)

    private val text =
        Body.Text("The curve flattens out past the first thousand holders, so early buys move the price most.")

    private val states = listOf(
        State.Loading,
        State.Unresolved,
        State.Resolved(source(Kind.PublicGroup), excerpt(text), reactionCount = 12),
        State.Resolved(
            source(Kind.PublicGroup, "Release Channel"),
            excerpt(Body.Text("v2.4 is out\n\n- Message links\n- Faster sync\n- Fixes")),
            reactionCount = 0,
        ),
        State.Resolved(
            source(Kind.PublicGroup),
            excerpt(Body.Photo(caption = Body.Text("Sunset from the roof"), photo = null, blurHash = null)),
            reactionCount = 0,
        ),
        State.Resolved(source(Kind.PublicGroup), excerpt(Body.Photo(caption = null, photo = null, blurHash = null)), reactionCount = 0),
        State.Resolved(source(Kind.DirectMessage, "Ada"), excerpt(Body.Cash("$5.00", tipped = true)), reactionCount = 0),
        State.Resolved(
            source(Kind.DirectMessage, "Ada"),
            excerpt(Body.CashLink("$20.00", LinkCard.Cash.Claim.Claimed)),
            reactionCount = 0,
        ),
        State.Deleted(source(Kind.PublicGroup)),
        State.Gated(source(Kind.PublicGroup), excerpt(text), requirement = null),
        State.Gated(
            source(Kind.PublicGroup),
            excerpt(text.copy(redacted = true)),
            requirement = LinkCard.GroupInvite.Requirement(amount = "$10", currencyName = "Moony", staffOnly = false),
        ),
        State.LockedGroup(source(Kind.PrivateGroup, "Inner Circle"), memberCount = 42, host = "Ada"),
        State.LockedDirect,
    )

    @Test
    fun rendersEveryState() = render(states, "message_link_card.png")

    private fun render(states: List<State>, fileName: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    states.forEach { state ->
                        MessageLinkCard(
                            card = LinkCard.Message(
                                url = "https://app.flipcash.com/chat/x/m/42",
                                start = 0,
                                end = 10,
                                chatId = chatId,
                                messageId = 42,
                                isGroup = true,
                                state = state,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onOpen = {},
                            modifier = Modifier.width(300.dp),
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
