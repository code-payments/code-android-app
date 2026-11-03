package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
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
 * Renders the cash bubble of every frame in the cash-bubbles design (section node 13266:348), with
 * the frames' amounts and names, in one PNG, for side-by-side comparison. A device reaches
 * only the roles its account plays, so this is the one place all ten are drawn. Not an assertion
 * test — it writes to `build/screenshots/cash_bubbles.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h2600dp-xhdpi")
class CashBubbleScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val alex = listOf<Byte>(2)
    private val viewer = listOf<Byte>(3)
    private val third = listOf<Byte>(4)

    private fun cash(amount: Double, parts: List<List<Byte>> = emptyList(), receiver: List<Byte>? = null) =
        MessageContent.Cash(
            intentId = listOf(0),
            amount = Fiat(fiat = amount, currencyCode = CurrencyCode.USD),
            mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa"),
            tokenName = "Jeffy",
            receiver = receiver,
            multiReceiver = parts.map { MessageContent.Cash.Payment(it, 1.0, 1_000_000L) },
        )

    private fun bubble(
        content: MessageContent.Cash,
        isFromSelf: Boolean,
        people: CashBubblePeople,
        paid: Boolean = false,
    ) = ChatListItem.ContentBubble(
        messageId = 42,
        contentIndex = 0,
        content = content,
        isFromSelf = isFromSelf,
        timestamp = Instant.fromEpochSeconds(1_000),
        receivedPart = if (paid) content.multiReceiver.firstOrNull { it.receiver == viewer } else null,
        cashPeople = people,
    )

    private val threeWay = cash(3.0, listOf(alex, viewer, third))
    private val twoWay = cash(2.0, listOf(alex, viewer))
    private val toNoah = cash(5.0, listOf(viewer)).copy(
        multiReceiver = listOf(MessageContent.Cash.Payment(viewer, 5.0, 5_000_000L)),
    )
    private val group = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Alex Morgan")
    private val groupOne = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Noah Parker")
    private val dm = CashBubblePeople.Direct(other = "Noah Parker")

    private val frames = listOf(
        "A" to bubble(threeWay, isFromSelf = true, people = group.copy(sender = null)),
        "A1" to bubble(twoWay, isFromSelf = true, people = group.copy(sender = null)),
        "B" to bubble(threeWay, isFromSelf = false, people = group, paid = true),
        "B1" to bubble(twoWay, isFromSelf = false, people = group, paid = true),
        "E" to bubble(threeWay, isFromSelf = false, people = group),
        "C" to bubble(cash(5.0, receiver = alex), isFromSelf = true, people = dm),
        "D" to bubble(cash(5.0, receiver = viewer), isFromSelf = false, people = dm),
        "G1" to bubble(toNoah, isFromSelf = true, people = groupOne.copy(sender = null)),
        "G2" to bubble(toNoah, isFromSelf = false, people = groupOne.copy(leadRecipient = null), paid = true),
        "G3" to bubble(toNoah, isFromSelf = false, people = groupOne),
    )

    @Test
    fun rendersEveryFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    frames.forEach { (name, item) ->
                        Text(text = name, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
                        ContentBubble(item = item, position = BubblePosition.Solo, interactive = false)
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, "cash_bubbles.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
