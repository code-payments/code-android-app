package com.getcode.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.text.AmountSizeStore.remember
import com.getcode.ui.components.text.NumberInputHelper.Companion.DECIMAL_SEPARATOR
import com.getcode.ui.components.text.NumberInputHelper.Companion.GROUPING_SEPARATOR
import java.util.Timer
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

interface AmountInputViewModel {
    fun onBackspace()
    fun onDot()
    fun onNumber(number: Int)
}

data class AmountAnimatedInputUiModel(
    val amountData: NumberInputHelper.AmountAnimatedData = NumberInputHelper.AmountAnimatedData(),
    val amountDataLast: NumberInputHelper.AmountAnimatedData = NumberInputHelper.AmountAnimatedData(),
    val lastPressedBackspace: Boolean = false
)

@ExperimentalAnimationApi
@Composable
internal fun AmountTextAnimated(
    currencyResId: Int?,
    amountPrefix: String,
    amountSuffix: String,
    placeholder: String = "0",
    uiModel: AmountAnimatedInputUiModel?,
    maxDigits: Int = 15,
    totalDecimals: Int = 2,
    textStyle: TextStyle,
    isClickable: Boolean,
) {
    uiModel ?: return

    val density = LocalDensity.current

    val staticX5 = CodeTheme.dimens.staticGrid.x5
    val staticX8 = CodeTheme.dimens.staticGrid.x8

    // Initialize visibility states based on uiModel
    val initialAmount = uiModel.amountData.amount
    val isInitiallyZero = initialAmount == "0" || initialAmount.isEmpty()
    var decimalPointVisibility by remember { mutableStateOf(initialAmount.contains(DECIMAL_SEPARATOR)) }
    var zeroVisibility by remember { mutableStateOf(isInitiallyZero) }
    val digitVisibility = remember {
        mutableStateListOf(*Array(maxDigits) { if (!isInitiallyZero) it < initialAmount.split(DECIMAL_SEPARATOR)[0].length else false })
    }
    val digitDecimalVisibility = remember { mutableStateListOf(*Array(totalDecimals) { false }) }
    val digitDecimalZeroVisibility = remember { mutableStateListOf(*Array(totalDecimals) { false }) }
    var firstDigit by remember { mutableStateOf(if (!isInitiallyZero && initialAmount.isNotEmpty()) initialAmount.first().toString() else "") }

    //Font states
    var textSize by remember { mutableStateOf(textStyle.fontSize) }
    val fontDecreasePoints = remember { HashMap<Int, Float>() }
    val maxFontSize = textStyle.fontSize

    val commaVisibility = uiModel.amountData.commaVisibility
    val amountSplit = uiModel.amountData.amount.split(DECIMAL_SEPARATOR)
    val amountLastSplit = uiModel.amountDataLast.amount.split(DECIMAL_SEPARATOR)

    val length1 = amountSplit[0].length
    val length2 = if (amountSplit.size > 1) amountSplit[1].length else 0
    val isDecimal = amountSplit.size > 1
    val isZero = amountSplit[0] == "0" || amountSplit[0].isEmpty()

    if (amountSplit.firstOrNull() != null && !isZero) {
        firstDigit = uiModel.amountData.amount.first().toString()
    }

    fun getValue(i1: Int, i2: Int): String? =
        amountSplit.getOrNull(i1)?.getOrNull(i2)?.toString()

    fun getLastValue(i1: Int, i2: Int): String? =
        amountLastSplit.getOrNull(i1)?.getOrNull(i2)?.toString()

    fun getComma(i: Int): Boolean =
        commaVisibility.getOrElse(i) { false }


    fun decreaseFont(length1: Int) {
        textSize = (textSize.value - 1).sp
        fontDecreasePoints[length1] = textSize.value
    }

    fun adjustFont(length1: Int) {
        if (isDecimal) return
        if (length1 < 4) {
            Timer().schedule(400) {
                textSize = maxFontSize
            }
        } else if (fontDecreasePoints.containsKey(length1)) {
            val size = fontDecreasePoints[length1] ?: return
            Timer().schedule(400) {
                textSize = size.sp
            }
            fontDecreasePoints.remove(length1)
        }
    }

    fun updateIsZero() {
        if (isZero) {
            for (i in 0 until digitVisibility.size)
                digitVisibility[i] = false
            zeroVisibility = true
        } else {
            for (i in 0 until digitVisibility.size)
                digitVisibility[i] = i < length1
            zeroVisibility = false
        }
    }

    fun onAmountChanged() {
        if (length1 >= maxDigits) return

        updateIsZero()

        if (isDecimal) {
            decimalPointVisibility = true
            when (length2) {
                0 -> {
                    digitDecimalZeroVisibility[0] = true
                    digitDecimalZeroVisibility[1] = true
                }

                1 -> {
                    digitDecimalZeroVisibility[0] = false
                    digitDecimalVisibility[0] = true
                }

                2 -> {
                    digitDecimalZeroVisibility[length2 - 1] = false
                    digitDecimalVisibility[length2 - 1] = true
                    digitDecimalZeroVisibility[0] = false
                    digitDecimalVisibility[0] = true
                }
            }
        } else {
            decimalPointVisibility = false
            digitDecimalZeroVisibility[0] = false
            digitDecimalVisibility[0] = false
            digitDecimalZeroVisibility[1] = false
            digitDecimalVisibility[1] = false
        }
    }

    fun onErase() {
        adjustFont(length1)
        if (amountSplit.size < 2) {
            updateIsZero()

            decimalPointVisibility = false
            digitDecimalZeroVisibility[0] = false
            digitDecimalZeroVisibility[1] = false
            digitDecimalVisibility[0] = false
            digitDecimalVisibility[1] = false
        } else {
            decimalPointVisibility = amountSplit[1].isNotEmpty() || isDecimal
            when (amountSplit[1].length) {
                0 -> {
                    digitDecimalZeroVisibility[0] = decimalPointVisibility
                    digitDecimalZeroVisibility[1] = decimalPointVisibility
                    digitDecimalVisibility[0] = false
                    digitDecimalVisibility[1] = false
                }

                1 -> {
                    digitDecimalZeroVisibility[0] = false
                    digitDecimalVisibility[0] = true
                    digitDecimalZeroVisibility[1] = true
                    digitDecimalVisibility[1] = false
                }

                2 -> {
                    digitDecimalZeroVisibility[0] = false
                    digitDecimalVisibility[0] = true
                    digitDecimalZeroVisibility[1] = false
                    digitDecimalVisibility[1] = true
                }
            }
        }
    }

    LaunchedEffect(amountSplit[0], if (amountSplit.size > 1) amountSplit[1] else 0) {
        onAmountChanged()
    }
    LaunchedEffect(uiModel.lastPressedBackspace, uiModel.amountData) {
        if (uiModel.lastPressedBackspace) {
            onErase()
        }
    }

    // Animation definitions
    val decimalEnter =
        slideInHorizontally(initialOffsetX = { with(density) { -(staticX5).roundToPx() } }) +
                expandHorizontally(expandFrom = Alignment.Start) +
                fadeIn(initialAlpha = 0.3f)

    val decimalExit =
        slideOutHorizontally(targetOffsetX = { with(density) { -(staticX5).roundToPx() } }) +
                shrinkHorizontally(shrinkTowards = Alignment.Start) +
                fadeOut(targetAlpha = 0.3f)

    val decimalZeroEnter = if (uiModel.lastPressedBackspace) {
        slideInVertically(initialOffsetY = { with(density) { AnimationMaxY.roundToPx() } }) +
                expandVertically(expandFrom = Alignment.Bottom) +
                fadeIn(initialAlpha = 0.3f)
    } else {
        slideInVertically(initialOffsetY = { with(density) { -(AnimationMaxY).roundToPx() } }) +
                expandVertically(expandFrom = Alignment.Top) +
                fadeIn(initialAlpha = 0.3f)
    }

    val decimalZeroExit = if (uiModel.lastPressedBackspace) {
        slideOutHorizontally() +
                shrinkHorizontally() +
                fadeOut()
    } else {
        slideOutVertically(targetOffsetY = { return@slideOutVertically 300 }) +
                shrinkVertically() +
                fadeOut()
    }

    val zeroEnter = slideInVertically(initialOffsetY = { with(density) { AnimationMaxY.roundToPx() } }) +
            expandVertically(expandFrom = Alignment.Bottom) +
            fadeIn(initialAlpha = 0.3f)

    val zeroExit = slideOutVertically(targetOffsetY = { return@slideOutVertically 300 }) +
            shrinkVertically() +
            fadeOut()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CodeTheme.dimens.grid.x2)
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val prefixPadding by animateDpAsState(
            if (amountSuffix.isEmpty()) CodeTheme.dimens.staticGrid.x2 else CodeTheme.dimens.staticGrid.x1
        )

        Spacer(modifier = Modifier.width(prefixPadding))

        if (currencyResId != null && currencyResId > 0) {
            Image(
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.grid.x7)
                    .clip(CircleShape)
                    .align(Alignment.CenterVertically),
                painter = painterResource(
                    currencyResId
                ),
                contentDescription = ""
            )
            if (isClickable) {
                Image(
                    modifier = Modifier
                        .width(CodeTheme.dimens.grid.x4)
                        .align(Alignment.CenterVertically),
                    painter = painterResource(R.drawable.ic_dropdown),
                    contentDescription = ""
                )
            } else {
                Spacer(Modifier.requiredWidth(CodeTheme.dimens.grid.x4))
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .width(CodeTheme.dimens.grid.x1)
        )

        Text(
            modifier = Modifier.fillMaxHeight(),
            text = amountPrefix,
            fontSize = textSize,
            fontWeight = FontWeight.Bold
        )

        AnimatedPlaceholderDigit(
            modifier = Modifier.fillMaxHeight(),
            text = firstDigit,
            digitVisible = digitVisibility[0],
            placeholder = placeholder,
            placeholderVisible = zeroVisibility,
            fontSize = textSize,
            density = density,
            placeholderEnter = zeroEnter,
            placeholderExit = zeroExit,
            placeholderColor = Color.White
        )

        for (i in 1 until maxDigits) {
            Digit(
                modifier = Modifier.fillMaxHeight(),
                isVisible = getComma(i = i),
                text = GROUPING_SEPARATOR.toString(),
                fontSize = textSize,
                density = density,
            )
            Digit(
                modifier = Modifier.fillMaxHeight(),
                isVisible = digitVisibility[i],
                text = getValue(0, i) ?: getLastValue(0, i) ?: "",
                fontSize = textSize,
                density = density
            )
        }

        Digit(
            modifier = Modifier.fillMaxHeight(),
            isVisible = decimalPointVisibility,
            text = DECIMAL_SEPARATOR.toString(),
            fontSize = textSize,
            density = density,
            enter = decimalEnter,
            exit = decimalExit,
        )

        for (i in 0 until totalDecimals) {
            AnimatedPlaceholderDigit(
                text = getValue(1, i) ?: getLastValue(1, i) ?: "0",
                digitVisible = digitDecimalVisibility[i],
                placeholderVisible = digitDecimalZeroVisibility[i],
                placeholder = "0",
                fontSize = textSize,
                density = density,
                placeholderEnter = decimalZeroEnter,
                placeholderExit = decimalZeroExit,
            )
        }

        Text(
            modifier = Modifier.padding(end = CodeTheme.dimens.grid.x3),
            text = amountSuffix.ifEmpty { " " },
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) {
                    decreaseFont(length1)
                }
            }
        )
    }
}

private fun defaultDigitEnter(initialOffsetY: Int): EnterTransition {
    return (slideInVertically(
        initialOffsetY = { initialOffsetY },
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = 80,
            easing = LinearOutSlowInEasing
        )
    ) + fadeIn())
}

@Composable
private fun defaultDigitExit(targetOffsetY: Int): ExitTransition {
    return fadeOut() + slideOutVertically(
        targetOffsetY = { targetOffsetY },
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )
}

@Composable
private fun Digit(
    isVisible: Boolean,
    text: String,
    fontSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    color: Color = Color.White,
) {
    val staticX4 = CodeTheme.dimens.staticGrid.x4
    val defaultEnter = defaultDigitEnter(initialOffsetY = with(density) { -(staticX4).roundToPx() })
    val enterTransition = remember(enter) {
        enter ?: defaultEnter
    }

    val defaultExit = defaultDigitExit(targetOffsetY = with(density) { -(staticX4).roundToPx() })
    val exitTransition = remember(exit) {
        exit ?: defaultExit
    }

    Row(
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isVisible,
            transitionSpec = { enterTransition togetherWith exitTransition }
        ) { visible ->
            if (visible) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else {
                Spacer(modifier = Modifier)
            }
        }
    }
}


private enum class PlaceholderState {
    Digit, Placeholder, None
}

@Composable
private fun AnimatedPlaceholderDigit(
    text: String,
    placeholder: String,
    digitVisible: Boolean,
    placeholderVisible: Boolean,
    fontSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier,
    placeholderEnter: EnterTransition,
    placeholderExit: ExitTransition,
    placeholderColor: Color = Color.White.copy(alpha = .2f),
) {
    val targetState = when {
        digitVisible -> PlaceholderState.Digit
        placeholderVisible -> PlaceholderState.Placeholder
        else -> PlaceholderState.None
    }

    val staticX4 = CodeTheme.dimens.staticGrid.x4
    val digitEnter = defaultDigitEnter(initialOffsetY = with(density) { -(staticX4).roundToPx() })
    val digitExit = defaultDigitExit(targetOffsetY = with(density) { -(staticX4).roundToPx() })

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            when {
                // Placeholder -> Digit: Placeholder exits, Digit enters
                initialState == PlaceholderState.Placeholder&& targetState == PlaceholderState.Digit -> {
                    digitEnter togetherWith placeholderExit
                }
                // Digit -> Placeholder: Digit exits, Placeholder enters
                initialState == PlaceholderState.Digit&& targetState == PlaceholderState.Placeholder -> {
                    placeholderEnter togetherWith digitExit
                }

                // None -> Placeholder: None exits, Placeholder enters
                initialState == PlaceholderState.None && targetState == PlaceholderState.Placeholder -> {
                    placeholderEnter togetherWith ExitTransition.None
                }

                else -> {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            }
        },
        modifier = modifier
    ) { state ->
        when (state) {
            PlaceholderState.Digit -> {
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            PlaceholderState.Placeholder -> {
                Text(
                    text = placeholder,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = placeholderColor
                )
            }

            PlaceholderState.None -> Unit
        }
    }
}

private val AnimationMaxY = 120.dp