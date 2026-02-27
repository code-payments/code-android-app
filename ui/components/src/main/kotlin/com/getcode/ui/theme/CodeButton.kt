package com.getcode.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import com.getcode.theme.Transparent
import com.getcode.theme.White
import com.getcode.theme.White10
import com.getcode.theme.White20
import com.getcode.theme.White50
import com.getcode.ui.components.R
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.utils.plus

enum class ButtonState {
    Bordered,
    Filled,
    Filled50,
    Filled20,
    Filled10,
    BgBlur20,
    Subtle,
    ;

    @Composable
    internal fun ripple() = ripple(
        bounded = true,
        color = when (this) {
            Bordered -> White
            Filled -> Black50
            Filled50 -> White50
            Filled20 -> White20
            Filled10 -> White10
            Subtle -> White
            BgBlur20 -> White20
        }
    )

    @Composable
    fun colors(
        textColor: Color = Color.Unspecified,
    ): ButtonColors {
        return when (this) {
            Filled -> ButtonDefaults.buttonColors(
                backgroundColor = White,
                contentColor = textColor.takeOrElse { Color(0XFF121212) },
                disabledBackgroundColor = White10,
                disabledContentColor = White50,
            )

            Bordered ->
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Transparent,
                    disabledContentColor = Color.White.copy(0.30f),
                    contentColor = textColor.takeOrElse { Color.LightGray }
                )

            Filled50 ->
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = White50,
                    disabledContentColor = White20,
                    contentColor = textColor.takeOrElse { White },
                )

            Filled20 ->
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = White20,
                    disabledContentColor = White20,
                    contentColor = textColor.takeOrElse { White },
                )

            Filled10 ->
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = White10,
                    disabledContentColor = White50,
                    contentColor = textColor.takeOrElse { White },
                )

            Subtle ->
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Transparent,
                    disabledContentColor = White50,
                    contentColor = textColor.takeOrElse { CodeTheme.colors.textSecondary }
                )

            BgBlur20 -> {
                ButtonDefaults.outlinedButtonColors(
                    backgroundColor = White20,
                    disabledContentColor = White20,
                    contentColor = textColor.takeOrElse { White },
                )
            }
        }
    }

    @Composable
    internal fun border(isEnabled: Boolean): BorderStroke {
        val border = CodeTheme.dimens.border
        return remember(this, isEnabled) {
            when (this) {
                Bordered -> BorderStroke(border, if (isEnabled) White50 else White20)
                else -> BorderStroke(border, Transparent)
            }
        }
    }
}

sealed interface ButtonContentState {
    data object Loading : ButtonContentState
    data object Successful : ButtonContentState
    data object Content : ButtonContentState
}

@Composable
fun CodeButtonSpacer(modifier: Modifier = Modifier) {
    CodeButton(
        modifier = modifier,
        buttonState = ButtonState.Subtle,
        enabled = false,
        text = "",
    ) {
    }
}

@Composable
fun CodeButton(
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        top = CodeTheme.dimens.grid.x2,
        bottom = CodeTheme.dimens.grid.x2,
    ),
    overrideContentPadding: Boolean = false,
    buttonState: ButtonState = ButtonState.Filled,
    textColor: Color = Color.Unspecified,
    shape: Shape = CodeTheme.shapes.small,
    style: TextStyle = CodeTheme.typography.textMedium,
    onClick: () -> Unit,
) {
    CodeButton(
        modifier = modifier,
        onClick = onClick,
        isLoading = isLoading,
        isSuccess = isSuccess,
        enabled = enabled,
        buttonState = buttonState,
        contentPadding = contentPadding,
        overrideContentPadding = overrideContentPadding,
        shape = shape,
        contentColor = textColor,
        style = style,
        sizeKey = text
    ) {
        Text(text = text)
    }
}

@Composable
fun CodeButton(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        top = CodeTheme.dimens.grid.x2,
        bottom = CodeTheme.dimens.grid.x2,
    ),
    overrideContentPadding: Boolean = false,
    buttonState: ButtonState = ButtonState.Filled,
    textColor: Color = Color.Unspecified,
    shape: Shape = CodeTheme.shapes.small,
    style: TextStyle = CodeTheme.typography.textMedium,
    onClick: () -> Unit,
) {
    CodeButton(
        modifier = modifier,
        onClick = onClick,
        isLoading = isLoading,
        isSuccess = isSuccess,
        enabled = enabled,
        buttonState = buttonState,
        contentPadding = contentPadding,
        overrideContentPadding = overrideContentPadding,
        shape = shape,
        contentColor = textColor,
        style = style,
        sizeKey = text,
    ) {
        val updatedInlineContent by rememberUpdatedState(inlineContent)
        Text(text = text, inlineContent = updatedInlineContent)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CodeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    buttonState: ButtonState = ButtonState.Filled,
    shape: Shape = CodeTheme.shapes.small,
    contentPadding: PaddingValues = PaddingValues(
        top = CodeTheme.dimens.grid.x2,
        bottom = CodeTheme.dimens.grid.x2,
    ),
    overrideContentPadding: Boolean = false,
    contentColor: Color = Color.Unspecified,
    style: TextStyle = CodeTheme.typography.textMedium,
    sizeKey: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val isEnabled by remember(enabled, isLoading, isSuccess) {
        derivedStateOf { enabled && !isLoading && !isSuccess }
    }
    val isSuccessful by remember(isSuccess) {
        derivedStateOf { isSuccess }
    }

    val colors = buttonState.colors(contentColor)
    val border = buttonState.border(isEnabled)
    val ripple = buttonState.ripple()

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentEnforcement provides false,
    ) {

        var size by remember(sizeKey) {
            mutableStateOf(DpSize.Unspecified)
        }

        val cp =
            (if (overrideContentPadding) PaddingValues(0.dp) else ButtonDefaults.ContentPadding).plus(
                contentPadding
            )

        val contentState by remember(isLoading, isSuccessful) {
            derivedStateOf {
                when {
                    isLoading -> ButtonContentState.Loading
                    isSuccessful -> ButtonContentState.Successful
                    else -> ButtonContentState.Content
                }
            }
        }

        CompositionLocalProvider(
            LocalContentColor provides colors.contentColor(enabled).value,
            LocalAbsoluteElevation provides 0.dp,
        ) {
            Box(
                modifier =
                    modifier
                        .minimumInteractiveComponentSize()
                        .shadow(0.dp, shape, clip = false)
                        .border(border, shape)
                        .background(color = colors.backgroundColor(enabled).value, shape = shape)
                        .addIf(buttonState == ButtonState.BgBlur20) {
                            Modifier.frostedGlass()
                        }
                        .clip(shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple,
                            enabled = enabled,
                            onClick = onClick,
                        ),
                propagateMinConstraints = true,
            ) {
                Row(
                    Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight,
                        )
                        .padding(cp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .addIf(size.isSpecified) { Modifier.size(size) }
                            .addIf(size.isUnspecified) { Modifier.measured { size = it } }
                            .width(IntrinsicSize.Max)
                            .height(IntrinsicSize.Min)
                    ) {
                        Crossfade(contentState) { state ->
                            when (state) {
                                ButtonContentState.Content -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ProvideTextStyle(
                                            value = style.copy(
                                                color = colors.contentColor(
                                                    enabled
                                                ).value
                                            )
                                        ) {
                                            this@Row.content()
                                        }
                                    }
                                }

                                ButtonContentState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CodeCircularProgressIndicator(
                                            strokeWidth = CodeTheme.dimens.thickBorder,
                                            color = colors.contentColor(enabled).value,
                                            modifier = Modifier
                                                .size(CodeTheme.dimens.grid.x3)
                                        )
                                    }
                                }

                                ButtonContentState.Successful -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            modifier = Modifier.requiredSize(CodeTheme.dimens.grid.x3),
                                            painter = painterResource(id = R.drawable.ic_check),
                                            tint = Color.White,
                                            contentDescription = "",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FROSTED_GLASS_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float blurRadius;
    uniform float noiseStrength;
    
    float random(float2 st) {
        return fract(sin(dot(st.xy, float2(12.9898, 78.233))) * 43758.5453123);
    }
    
    half4 main(float2 coord) {
        half4 color = content.eval(coord);
        
        // Add subtle noise for frosted texture
        float noise = random(coord / resolution) * noiseStrength;
        color.rgb += half3(noise);
        
        return color;
    }
"""

fun Modifier.frostedGlass(
    tint: Color = Color.White.copy(alpha = 0.1f),
    noiseStrength: Float = 0.03f,
    shape: Shape = RectangleShape
): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = RuntimeShader(FROSTED_GLASS_SHADER)
        Modifier
            .drawWithContent {
                drawRect(tint)

                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("noiseStrength", noiseStrength)
                drawRect(brush = ShaderBrush(shader))

                drawContent()
            }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // API 31-32 fallback (no RuntimeShader)
        Modifier
            .drawWithContent {
                drawRect(tint)
                drawContent()
            }
    } else {
        // Pre-API 31 fallback - just tinted background
        Modifier
            .clip(shape)
            .background(tint)
    }
)