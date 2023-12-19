package com.getcode.theme

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController


private val DarkColorPalette = CodeColors(
    brand = Brand,
    brandSecondary = BrandLight,
    background = Brand,
    onBackground = White
)

@Composable
fun CodeTheme(
    content: @Composable () -> Unit
) {
    val colors = DarkColorPalette
    val sysUiController = rememberSystemUiController()

    SideEffect {
        sysUiController.setStatusBarColor(color = Color.Transparent)
        sysUiController.setSystemBarsColor(color = Color.Transparent)
    }

    ProvideCodeColors(colors) {
        MaterialTheme(
            //colors = debugColors(),
            typography = typography,
            content = content,
            shapes = shapes
        )
    }
}

object CodeTheme {
    val colors: CodeColors
        @Composable
        get() = LocalCodeColors.current
}

@Stable
class CodeColors(
    brand: Color,
    brandSecondary: Color,
    background: Color,
    onBackground: Color,
) {
    var brand by mutableStateOf(brand)
        private set
    var brandSecondary by mutableStateOf(brandSecondary)
        private set
    var background by mutableStateOf(background)
        private set
    var onBackground by mutableStateOf(onBackground)
        private set

    fun update(other: CodeColors) {
        brand = other.brand
        brandSecondary = other.brandSecondary
        background = other.background
        onBackground = other.onBackground
    }

    fun copy(): CodeColors = CodeColors(
        brand = brand,
        brandSecondary = brandSecondary,
        background = background,
        onBackground = onBackground,
    )
}

@Composable
fun ProvideCodeColors(
    colors: CodeColors,
    content: @Composable () -> Unit
) {
    val colorPalette = remember { colors.copy() }
    colorPalette.update(colors)
    CompositionLocalProvider(LocalCodeColors provides colorPalette, content = content)
}

val LocalCodeColors = staticCompositionLocalOf<CodeColors> {
    error("No ColorPalette provided")
}

/**
 * A Material [Colors] implementation which sets all colors to [debugColor] to discourage usage of
 * [MaterialTheme.colors] in preference to [CodeTheme.colors].
 */
fun debugColors(
    darkTheme: Boolean = true,
    debugColor: Color = Color.Magenta
) = Colors(
    primary = debugColor,
    primaryVariant = debugColor,
    secondary = debugColor,
    secondaryVariant = debugColor,
    background = debugColor,
    surface = debugColor,
    error = debugColor,
    onPrimary = debugColor,
    onSecondary = debugColor,
    onBackground = debugColor,
    onSurface = debugColor,
    onError = debugColor,
    isLight = !darkTheme
)

@Composable
fun inputColors() = TextFieldDefaults.textFieldColors(
    textColor = Color.White,
    disabledTextColor = Color.White,
    backgroundColor = White05,
    placeholderColor = White50,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Color.White,
)
