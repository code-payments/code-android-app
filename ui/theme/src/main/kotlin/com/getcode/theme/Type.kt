package com.getcode.theme

import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val Avenir = FontFamily(
    Font(R.font.avenir_next_regular, FontWeight.Thin),
    Font(R.font.avenir_next_regular, FontWeight.ExtraLight),
    Font(R.font.avenir_next_regular, FontWeight.Light),
    Font(R.font.avenir_next_regular, FontWeight.Normal),
    Font(R.font.avenir_next_demi, FontWeight.Medium),
    Font(R.font.avenir_next_demi, FontWeight.SemiBold),
    Font(R.font.avenir_next_demi, FontWeight.Bold),
    Font(R.font.avenir_next_demi, FontWeight.ExtraBold),
    Font(R.font.avenir_next_demi, FontWeight.Black)
)

private val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_variable, FontWeight.Light),
    Font(R.font.roboto_mono_variable, FontWeight.Normal),
    Font(R.font.roboto_mono_variable, FontWeight.Medium),
    Font(R.font.roboto_mono_variable, FontWeight.SemiBold)
)

data class CodeTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val displayExtraSmall: TextStyle,
    val keyboard: TextStyle,
    val screenTitle: TextStyle,
    val textLarge: TextStyle,
    val textMedium: TextStyle,
    val textSmall: TextStyle,
    val caption: TextStyle,
    val linkMedium: TextStyle,
    val linkSmall: TextStyle,
)

val LocalCodeTypography = staticCompositionLocalOf<CodeTypography> {
    error("No Type system provided")
}

@Composable
fun ProvideTypography(
    typography: CodeTypography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCodeTypography provides typography, content = content)
}

internal val codeTypography = CodeTypography(
    displayLarge = TextStyle(
        fontFamily = Avenir,
        fontSize = 55.sp,
        fontWeight = FontWeight.Bold,
    ),
    displayMedium = TextStyle(
        fontFamily = Avenir,
        fontSize = 40.sp,
        fontWeight = FontWeight.Medium,
    ),
    displaySmall = TextStyle(
        fontFamily = Avenir,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    displayExtraSmall = TextStyle(
        fontFamily = Avenir,
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp
    ),
    screenTitle = TextStyle(
        fontFamily = Avenir,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        //letterSpacing = 0.1.sp
    ),
    textLarge = TextStyle(
        fontFamily = Avenir,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
    ),
    textMedium = TextStyle( //Text Medium
        fontFamily = Avenir,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    textSmall = TextStyle(
        //Text Small
        fontFamily = Avenir,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    ),
    caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = Avenir,
        fontWeight = FontWeight.Medium,
        color = White50,
    ),
    linkMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFamily = Avenir,
        fontWeight = FontWeight.Normal,
        textDecoration = TextDecoration.Underline,
    ),
    linkSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontFamily = Avenir,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline,
    ),
    keyboard = TextStyle(
        fontSize = 30.sp,
        fontFamily = Avenir,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
)

internal fun CodeTypography.toMaterial(): Typography = Typography(
    h1 = displayMedium.bolded(),
    h3 = displaySmall,
    body1 = textMedium,
    body2 = textSmall,
    button = textMedium,
    subtitle1 = textLarge,
    subtitle2 = screenTitle,
    caption = caption
)


fun TextStyle.with(fontFamily: FontFamily? = this.fontFamily, weight: FontWeight? = this.fontWeight): TextStyle =
    copy(fontFamily = fontFamily, fontWeight = weight)

fun TextStyle.monospace(weight: FontWeight? = this.fontWeight) = with(RobotoMono, weight)
fun TextStyle.bolded() = with(weight = FontWeight.W700)
