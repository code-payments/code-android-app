package com.getcode.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
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

internal val CodeDefaultColorScheme = ColorScheme(
    brand = Brand,
    brandLight = BrandLight,
    brandSubtle = BrandSubtle,
    brandMuted = BrandMuted,
    brandDark = BrandDark,
    brandOverlay = BrandOverlay,
    secondary = BrandAccent,
    tertiary = BrandAccent,
    action = Gray50,
    onAction = White,
    background = Brand,
    onBackground = White,
    surface = Brand,
    onSurface = White,
    error = Error,
    errorText = TextError,
    textMain = TextMain,
    textSecondary = TextSecondary
)

@Composable
fun DesignSystem(
    colorScheme: ColorScheme = CodeDefaultColorScheme,
    typography: CodeTypography = codeTypography,
    content: @Composable () -> Unit
) {
    val sysUiController = rememberSystemUiController()

    SideEffect {
        sysUiController.setStatusBarColor(color = Color(0x01000000))
        sysUiController.setNavigationBarColor(color = Color(0x01000000))
    }

    val dimensions = calculateDimensions()

    ProvideColorScheme(colorScheme) {
        ProvideTypography(typography = typography) {
            ProvideDimens(dimensions = dimensions) {
                MaterialTheme(
                    colors = debugColors(),
                    shapes = shapes,
                    typography = LocalCodeTypography.current.toMaterial()
                ) {
                    // setup after MDC theme to override defaults in theme
                    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                        content()
                    }
                }
            }
        }
    }
}

object CodeTheme {
    val colors: ColorScheme
        @Composable get() = LocalCodeColors.current
    val dimens: Dimensions
        @Composable get() = LocalDimens.current
    val typography: CodeTypography
        @Composable get() = LocalCodeTypography.current
    val shapes: Shapes
        @Composable get() = MaterialTheme.shapes
}

@Stable
class ColorScheme(
    brand: Color,
    brandLight: Color,
    brandSubtle: Color,
    brandMuted: Color,
    brandDark: Color,
    brandOverlay: Color,
    secondary: Color,
    tertiary: Color,
    action: Color,
    onAction: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    error: Color,
    errorText: Color,
    textMain: Color,
    textSecondary: Color,
) {
    var brand by mutableStateOf(brand)
        private set
    var brandLight by mutableStateOf(brandLight)
        private set
    var brandSubtle by mutableStateOf(brandSubtle)
        private set
    var brandMuted by mutableStateOf(brandMuted)
        private set
    var brandDark by mutableStateOf(brandDark)
        private set
    var brandOverlay by mutableStateOf(brandOverlay)
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
    var textMain by mutableStateOf(textMain)
        private set
    var textSecondary by mutableStateOf(textSecondary)
        private set
    var secondary by mutableStateOf(secondary)
        private set
    var tertiary by mutableStateOf(tertiary)
        private set
    var action by mutableStateOf(action)
        private set
    var onAction by mutableStateOf(onAction)
        private set

    fun update(other: ColorScheme) {
        brand = other.brand
        brandLight = other.brandLight
        brandSubtle = other.brandSubtle
        brandMuted = other.brandMuted
        brandDark = other.brandDark
        brandOverlay = other.brandOverlay
        background = other.background
        onBackground = other.onBackground
        surface = other.surface
        onSurface = other.onSurface
        error = other.error
        errorText = other.errorText
        textMain = other.textMain
        textSecondary = other.textSecondary
        secondary = other.secondary
        tertiary = other.tertiary
        action = other.action
        onAction = other.onAction
    }

    fun copy(): ColorScheme = ColorScheme(
        brand = brand,
        brandLight = brandLight,
        brandSubtle = brandSubtle,
        brandMuted = brandMuted,
        brandDark = brandDark,
        brandOverlay = brandOverlay,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        error = error,
        errorText = errorText,
        textMain = textMain,
        textSecondary = textSecondary,
        secondary = secondary,
        tertiary = tertiary,
        action = action,
        onAction = onAction
    )
}

@Composable
fun ProvideColorScheme(
    colors: ColorScheme,
    content: @Composable () -> Unit
) {
    val colorPalette = remember { colors.copy() }
    colorPalette.update(colors)
    CompositionLocalProvider(LocalCodeColors provides colorPalette, content = content)
}

val LocalCodeColors = staticCompositionLocalOf<ColorScheme> {
    error("No ColorPalette provided")
}

/**
 * A Material [Colors] implementation which sets all colors to [debugColor] to discourage usage of
 * [MaterialTheme.colors] in preference to [DesignSystem.colors].
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
fun inputColors(
    textColor: Color = Color.White,
    disabledTextColor: Color = Color.White,
    borderColor: Color = CodeTheme.colors.brandLight,
    unfocusedBorderColor: Color = borderColor,
    backgroundColor: Color = White05,
    placeholderColor: Color = White50,
    cursorColor: Color = Color.White,
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = textColor,
    disabledTextColor = disabledTextColor,
    backgroundColor = backgroundColor,
    placeholderColor = placeholderColor,
    focusedBorderColor = borderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    cursorColor = cursorColor,
)