package xyz.flipchat.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandMuted
import com.getcode.theme.BrandOverlay
import com.getcode.theme.CashBill
import com.getcode.theme.CodeTypography
import com.getcode.theme.ColorScheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.Error
import com.getcode.theme.Gray50
import com.getcode.theme.TextMain
import com.getcode.theme.White
import com.getcode.theme.codeTypography

val FC_Primary = Color(0xFF362774)
private val FC_Secondary = Color(0xFF443091)
private val FC_Tertiary = Color(0xFF7D6CC3)
private val FC_TextWithPrimary = Color(0xFFD2C6FF)
private val FC_Accent = Color(0xFFC372FF)

private val colors = ColorScheme(
    brand = FC_Primary,
    brandLight = BrandLight,
    brandSubtle = FC_Secondary,
    brandMuted = BrandMuted,
    brandDark = Color(0xFF2C2158),
    brandOverlay = BrandOverlay,
    brandContainer = FC_Primary,
    secondary = FC_Secondary,
    tertiary = FC_Tertiary,
    indicator = FC_Accent,
    action = Gray50,
    onAction = White,
    background = FC_Primary,
    onBackground = White,
    surface = Color(0xFF28176E),
    surfaceVariant = FC_Secondary,
    onSurface = White,
    error = Error,
    errorText = Alert,
    success = FC_Accent,
    textMain = TextMain,
    textSecondary = FC_TextWithPrimary,
    divider = FC_Secondary,
    dividerVariant = FC_Tertiary,
    trackColor = Color(0xFF241A4B),
    cashBill = CashBill
)

@Composable
fun FlipchatTheme(content: @Composable () -> Unit) {
    DesignSystem(
        colorScheme = colors,
        // override code type system to make screen title's slightly bigger
        typography = codeTypography.copy(
            screenTitle = codeTypography.displayExtraSmall.copy(fontWeight = FontWeight.W500)
        ),
        content = content
    )
}