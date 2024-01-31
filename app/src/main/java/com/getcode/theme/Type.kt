package com.getcode.theme

import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.getcode.R

private val Avenir = FontFamily(
    Font(R.font.avenir_next_regular, FontWeight.Thin),
    Font(R.font.avenir_next_regular, FontWeight.ExtraLight),
    Font(R.font.avenir_next_regular, FontWeight.Light),
    Font(R.font.avenir_next_regular, FontWeight.Normal),
    Font(R.font.avenir_next_medium, FontWeight.Medium),
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

internal val typography = Typography(
    h1 = TextStyle(
        fontFamily = Avenir,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold
    ),
    h2 = TextStyle(
        fontFamily = Avenir,
        fontSize = 60.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 73.sp,
        letterSpacing = (-0.5).sp
    ),
    h3 = TextStyle( //Display Small
        fontFamily = Avenir,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    h4 = TextStyle(
        fontFamily = Avenir,
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 37.sp
    ),
    h5 = TextStyle(
        fontFamily = Avenir,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 29.sp
    ),
    h6 = TextStyle(
        fontFamily = Avenir,
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    subtitle1 = TextStyle( //Text Large
        fontFamily = Avenir,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
    ),
    subtitle2 = TextStyle( //Screen Title
        fontFamily = Avenir,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        //letterSpacing = 0.1.sp
    ),
    body1 = TextStyle( //Text Medium
        fontFamily = Avenir,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    body2 = TextStyle( //Text Small
        fontFamily = Avenir,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    ),
    button = TextStyle(
        fontFamily = Avenir,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center
    ),
    caption = TextStyle(
        fontFamily = Avenir,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.sp,
        //letterSpacing = 0.4.sp
    ),
    overline = TextStyle(
        fontFamily = Avenir,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
//        letterSpacing = 1.sp
    )
)

val Typography.robotoMono: FontFamily
    @Composable get() = RobotoMono

/**
 * custom displayLarge until M3 migration
 */
val Typography.displayLarge: TextStyle
    @Composable get() = h1.copy(fontSize = 55.sp)

fun TextStyle.withRobotoMono(weight: FontWeight? = this.fontWeight) = with(RobotoMono, weight)
fun TextStyle.with(fontFamily: FontFamily, weight: FontWeight? = this.fontWeight): TextStyle = copy(fontFamily = fontFamily, fontWeight = weight)