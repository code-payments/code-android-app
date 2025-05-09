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
    brandContainer = Brand,
    secondary = BrandAccent,
    tertiary = BrandAccent,
    indicator = BrandIndicator,
    action = Gray50,
    onAction = White,
    background = Brand,
    onBackground = White,
    surface = Brand,
    surfaceVariant = BrandDark,
    onSurface = White,
    error = Error,
    errorText = TextError,
    success = Success,
    textMain = TextMain,
    textSecondary = TextSecondary,
    divider = White10,
    dividerVariant = White05,
    trackColor = BrandSlideToConfirm,
    cashBill = CashBill,
    cashBillDecorColor = CashBillDecor
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
    brandContainer: Color,
    secondary: Color,
    tertiary: Color,
    indicator: Color,
    action: Color,
    onAction: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurface: Color,
    divider: Color,
    dividerVariant: Color,
    error: Color,
    errorText: Color,
    success: Color,
    textMain: Color,
    textSecondary: Color,
    trackColor: Color,
    cashBill: Color,
    cashBillDecorColor: Color,
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
    var brandContainer by mutableStateOf(brandContainer)
        private set
    var background by mutableStateOf(background)
        private set
    var onBackground by mutableStateOf(onBackground)
        private set
    var surface by mutableStateOf(surface)
        private set
    var surfaceVariant by mutableStateOf(surfaceVariant)
        private set
    var onSurface by mutableStateOf(onSurface)
        private set
    var error by mutableStateOf(error)
        private set
    var errorText by mutableStateOf(errorText)
        private set
    var success by mutableStateOf(success)
        private set
    var textMain by mutableStateOf(textMain)
        private set
    var textSecondary by mutableStateOf(textSecondary)
        private set
    var secondary by mutableStateOf(secondary)
        private set
    var tertiary by mutableStateOf(tertiary)
        private set
    var indicator by mutableStateOf(indicator)
        private set
    var action by mutableStateOf(action)
        private set
    var onAction by mutableStateOf(onAction)
        private set
    var divider by mutableStateOf(divider)
        private set
    var dividerVariant by mutableStateOf(dividerVariant)
        private set
    var trackColor by mutableStateOf(trackColor)
        private set
    var cashBillColor by mutableStateOf(cashBill)
        private set
    var cashBillDecorColor by mutableStateOf(cashBill)
        private set

    fun update(other: ColorScheme) {
        brand = other.brand
        brandLight = other.brandLight
        brandSubtle = other.brandSubtle
        brandMuted = other.brandMuted
        brandDark = other.brandDark
        brandOverlay = other.brandOverlay
        brandContainer = other.brandContainer
        background = other.background
        onBackground = other.onBackground
        surface = other.surface
        surfaceVariant = other.surfaceVariant
        onSurface = other.onSurface
        error = other.error
        errorText = other.errorText
        success = other.success
        textMain = other.textMain
        textSecondary = other.textSecondary
        secondary = other.secondary
        tertiary = other.tertiary
        indicator = other.indicator
        action = other.action
        onAction = other.onAction
        divider = other.divider
        dividerVariant = other.dividerVariant
        trackColor = other.trackColor
        cashBillColor = other.cashBillColor
        cashBillDecorColor = other.cashBillDecorColor
    }

    fun copy(): ColorScheme = ColorScheme(
        brand = brand,
        brandLight = brandLight,
        brandSubtle = brandSubtle,
        brandMuted = brandMuted,
        brandDark = brandDark,
        brandOverlay = brandOverlay,
        brandContainer = brandContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onSurface = onSurface,
        error = error,
        errorText = errorText,
        success = success,
        textMain = textMain,
        textSecondary = textSecondary,
        secondary = secondary,
        tertiary = tertiary,
        indicator = indicator,
        action = action,
        onAction = onAction,
        divider = divider,
        dividerVariant = dividerVariant,
        trackColor = trackColor,
        cashBill = cashBillColor,
        cashBillDecorColor = cashBillDecorColor,
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
    errorBorderColor: Color = CodeTheme.colors.error,
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = textColor,
    disabledTextColor = disabledTextColor,
    backgroundColor = backgroundColor,
    placeholderColor = placeholderColor,
    focusedBorderColor = borderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    cursorColor = cursorColor,
    errorBorderColor = errorBorderColor
)