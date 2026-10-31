package com.flipcash.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.utils.ConstraintMode
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The wallet's card amounts in the desktop Wallet pane are squeezed by the appreciation badge beside
 * them. Every character must keep its own room there: a digit with no room is drawn on top of its
 * neighbour.
 */
class AnimatedNumberTextFitTest {
    @get:Rule val rule = createComposeRule()

    private var width by mutableStateOf(0)
    private var value by mutableStateOf("")

    @Test
    fun `card amounts keep every character apart at every width`() {
        rule.setContent { FlipcashTheme { CardHeader(width, value) } }
        val bad = mutableListOf<String>()
        for (w in 180..320 step 6) {
            for (v in listOf("$190.96", "$191.20", "$32.92", "$111.19", "$1,234.56")) {
                width = w
                value = v
                rule.mainClock.advanceTimeBy(2_000)
                rule.waitForIdle()
                problem(v)?.let { bad += "$w dp, $v: $it" }
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    /** The token card's header row: name on the left, the appreciation badge and balance on the right. */
    @Composable
    private fun CardHeader(width: Int, value: String) {
        Row(
            Modifier.width(width.dp),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Waylon", Modifier.padding(start = 32.dp), style = CodeTheme.typography.textSmall)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("-$43.91", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = CodeTheme.typography.caption)
                Box(Modifier.weight(1f, fill = false).testTag("box")) {
                    AnimatedNumberText(
                        value = value,
                        style = CodeTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                        constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.textMedium),
                    )
                }
            }
        }
    }

    private fun problem(shown: String): String? {
        val box = rule.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot
        val cells = rule.onAllNodesWithText("", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text }?.length == 1 }
            .filter { it.boundsInRoot.left >= box.left - 40f }
            .sortedBy { it.boundsInRoot.left }
        fun text(i: Int) = cells[i].config[SemanticsProperties.Text].joinToString("") { it.text }
        for (i in 0 until cells.size - 1) {
            val a = cells[i].boundsInRoot
            val b = cells[i + 1].boundsInRoot
            if (b.left < a.left + a.width * 0.6f) return "'${text(i)}' at $a overlaps '${text(i + 1)}' at $b (box $box)"
        }
        val right = cells.lastOrNull()?.boundsInRoot?.right ?: return "nothing drawn for $shown"
        if (right > box.right + 1f) return "ends at $right past the box at ${box.right}"
        return null
    }
}
