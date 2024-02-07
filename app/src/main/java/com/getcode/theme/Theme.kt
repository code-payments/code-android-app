package com.getcode.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Typography
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
    brandLight = BrandLight,
    brandSubtle = BrandSubtle,
    brandMuted = BrandMuted,
    background = Brand,
    onBackground = White,
    surface = Brand,
    onSurface = White,
    error = topError,
    errorText = errorText
)

@Composable
fun CodeTheme(
    content: @Composable () -> Unit
) {
    val colors = DarkColorPalette
    val sysUiController = rememberSystemUiController()

    SideEffect {
        sysUiController.setStatusBarColor(color = Color(0x01000000))
        sysUiController.setNavigationBarColor(color = Color(0x01000000))
    }

    val dimensions = calculateDimensions()

    ProvideCodeColors(colors) {
        ProvideDimens(dimensions = dimensions) {
            MaterialTheme(
                colors = debugColors(),
                typography = typography,
                shapes = shapes
            ) {
                // setup after MDC theme to override defaults in theme
                CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                    content()
                }
            }
        }
    }
}

object CodeTheme {
    val colors: CodeColors
        @Composable get() = LocalCodeColors.current
    val dimens: Dimensions
        @Composable get() = LocalDimens.current
    val typography: Typography
        @Composable get() = MaterialTheme.typography
    val shapes: Shapes
        @Composable get() = MaterialTheme.shapes
}

@Stable
class CodeColors(
    brand: Color,
    brandLight: Color,
    brandSubtle: Color,
    brandMuted: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    error: Color,
    errorText: Color
) {
    var brand by mutableStateOf(brand)
        private set
    var brandLight by mutableStateOf(brandLight)
        private set
    var brandSubtle by mutableStateOf(brandSubtle)
        private set
    var brandMuted by mutableStateOf(brandMuted)
        private set
    var background by mutableStateOf(background)
        private set
    var onBackground by mutableStateOf(onBackground)
        private set
    var surface by mutableStateOf(surface)
        private set
    var onSurface by mutableStateOf(onSurface)
        private set
    var error by mutableStateOf(error)
        private set
    var errorText by mutableStateOf(errorText)
        private set

    fun update(other: CodeColors) {
        brand = other.brand
        brandLight = other.brandLight
        brandSubtle = other.brandSubtle
        brandMuted = other.brandMuted
        background = other.background
        onBackground = other.onBackground
        surface = other.surface
        onSurface = other.onSurface
        error = other.error
        errorText = other.errorText
    }

    fun copy(): CodeColors = CodeColors(
        brand = brand,
        brandLight = brandLight,
        brandSubtle = brandSubtle,
        brandMuted = brandMuted,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        error = error,
        errorText = errorText
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
