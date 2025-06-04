package com.getcode.ui.components.text

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.text.NumberInputHelper.Companion.DECIMAL_SEPARATOR
import com.getcode.ui.components.text.NumberInputHelper.Companion.GROUPING_SEPARATOR
import com.getcode.ui.components.text.animated.AnimatedPlaceholderDigit
import com.getcode.ui.components.text.animated.Digit
import com.getcode.ui.components.text.animated.NormalizedPrefixText
import java.util.Timer
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

        if (isDecimal && totalDecimals > 0) {
            decimalPointVisibility = true
            when (length2) {
                0 -> {
                    for (i in 0 until totalDecimals) {
                        digitDecimalZeroVisibility[i] = true
                    }
                }
                else -> {
                    for (i in 0 until totalDecimals) {
                        if (i < length2) {
                            digitDecimalZeroVisibility[i] = false
                            digitDecimalVisibility[i] = true
                        } else {
                            digitDecimalZeroVisibility[i] = true
                            digitDecimalVisibility[i] = false
                        }
                    }
                }
            }
        } else {
            decimalPointVisibility = false
            if (totalDecimals > 0) {
                for (i in 0 until totalDecimals) {
                    digitDecimalZeroVisibility[i] = false
                    digitDecimalVisibility[i] = false
                }
            }
        }
    }

    fun onErase() {
        adjustFont(length1)
        if (amountSplit.size < 2 || totalDecimals == 0) {
            updateIsZero()
            decimalPointVisibility = false
            if (totalDecimals > 0) {
                for (i in 0 until totalDecimals) {
                    digitDecimalZeroVisibility[i] = false
                    digitDecimalVisibility[i] = false
                }
            }
        } else {
            decimalPointVisibility = amountSplit[1].isNotEmpty() || isDecimal
            when (amountSplit[1].length) {
                0 -> {
                    for (i in 0 until totalDecimals) {
                        digitDecimalZeroVisibility[i] = decimalPointVisibility
                        digitDecimalVisibility[i] = false
                    }
                }
                else -> {
                    for (i in 0 until totalDecimals) {
                        if (i < amountSplit[1].length) {
                            digitDecimalZeroVisibility[i] = false
                            digitDecimalVisibility[i] = true
                        } else {
                            digitDecimalZeroVisibility[i] = true
                            digitDecimalVisibility[i] = false
                        }
                    }
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
            .height(IntrinsicSize.Min),
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
                    .clip(CircleShape),
                painter = painterResource(currencyResId),
                contentDescription = ""
            )
            if (isClickable) {
                Image(
                    modifier = Modifier
                        .width(CodeTheme.dimens.grid.x4),
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


        NormalizedPrefixText(
            amountPrefix = amountPrefix,
            textStyle = textStyle,
            textSize = textSize,
        ) {
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
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }

        Text(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = CodeTheme.dimens.grid.x3),
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

private val AnimationMaxY = 120.dp