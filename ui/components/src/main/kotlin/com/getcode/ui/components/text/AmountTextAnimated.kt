package com.getcode.ui.components.text

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.text.NumberInputHelper.Companion.DECIMAL_SEPARATOR
import com.getcode.ui.components.text.NumberInputHelper.Companion.GROUPING_SEPARATOR
import java.util.*
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

private val AnimationMaxY = 120.dp

@Composable
fun Digit(
    isVisible: Boolean,
    text: String,
    fontSize: TextUnit,
    density: Density,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    color: Color = Color.White,
) {
    Row {
        val staticX4 = CodeTheme.dimens.staticGrid.x4

        AnimatedVisibility(
            visible = isVisible,
            enter = enter
                ?: (slideInVertically(
                    initialOffsetY = { with(density) { -(staticX4).roundToPx() } },
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = 80,
                        easing = LinearOutSlowInEasing
                    )
                )
                + expandHorizontally(
                    animationSpec = tween(durationMillis = 80, easing = LinearOutSlowInEasing),
                    expandFrom = Alignment.CenterHorizontally
                )
                + fadeIn()),
            exit = exit
                ?: (slideOutVertically(
                    targetOffsetY = { with(density) { -(staticX4).roundToPx() } },
                    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                )
                + shrinkHorizontally(
                    animationSpec = tween(
                        durationMillis = 80,
                        delayMillis = 300,
                        easing = LinearOutSlowInEasing
                    ),
                    shrinkTowards = Alignment.CenterHorizontally
                )
                + fadeOut())
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}


@Composable
fun AnimatedPlaceholderDigit(
    text: String,
    placeholder: String,
    digitVisible: Boolean,
    placeholderVisible: Boolean,
    fontSize: TextUnit,
    density: Density,
    placeholderEnter: EnterTransition? = null,
    placeholderExit: ExitTransition? = null,
    placeholderColor: Color = Color.White.copy(alpha = .2f))
{
    if (placeholderVisible || digitVisible) {
        Box {
            Row {
                Digit(
                    isVisible = placeholderVisible,
                    text = placeholder,
                    fontSize = fontSize,
                    density = density,
                    enter = placeholderEnter,
                    exit = placeholderExit,
                    color = placeholderColor
                )
            }
            Row {
                Digit(
                    isVisible = digitVisible,
                    text = text,
                    fontSize = fontSize,
                    density = density,
                )
            }
        }
    }
}


@ExperimentalAnimationApi
@Composable
internal fun AmountTextAnimated(
    currencyResId: Int?,
    amountPrefix: String,
    amountSuffix: String,
    placeholder: String = "0",
    uiModel: AmountAnimatedInputUiModel?,
    textStyle: TextStyle,
    isClickable: Boolean,
) {
    uiModel ?: return

    val density = LocalDensity.current

    val staticX8 = CodeTheme.dimens.staticGrid.x8

    //Maximum possible values
    val maxDigits = 10
    val totalDecimal = 2

    //Visibility states
    var decimalPointVisibility by remember { mutableStateOf(false) }
    var zeroVisibility by remember { mutableStateOf(true) }
    val digitVisibility = remember { mutableStateListOf(*Array(maxDigits) { it == 0 }) }
    val digitDecimalVisibility = remember { mutableStateListOf(*Array(totalDecimal) { false }) }
    val digitDecimalZeroVisibility = remember { mutableStateListOf(*Array(totalDecimal) { false }) }
    var firstDigit by remember { mutableStateOf("") }

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
                        digitDecimalZeroVisibility[length2-1] = false
                        digitDecimalVisibility[length2-1] = true
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
            when(amountSplit[1].length) {
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

    LaunchedEffect(key1 = amountSplit[0], key2 = if (amountSplit.size > 1) amountSplit[1] else 0) {
        onAmountChanged()
    }
    LaunchedEffect(key1 = uiModel.lastPressedBackspace, key2 = uiModel.amountData) {
        if (uiModel.lastPressedBackspace) {
            onErase()
        }
    }

    val decimalEnter = slideInHorizontally(initialOffsetX = { with(density) { -(staticX8).roundToPx() } }) +
            expandHorizontally(expandFrom = Alignment.Start) +
            fadeIn(initialAlpha = 0.3f)

    val decimalZeroEnter =
        if (uiModel.lastPressedBackspace)
            slideInVertically(initialOffsetY = { with(density) { AnimationMaxY.roundToPx() } }) +
                    expandVertically(expandFrom = Alignment.Bottom) +
                    fadeIn(initialAlpha = 0.3f)
        else
            slideInHorizontally(initialOffsetX = { with(density) { -(staticX8).roundToPx() } }) +
                    expandHorizontally(expandFrom = Alignment.Start) +
                    fadeIn(initialAlpha = 0.3f)

    val decimalZeroExit =
        if (uiModel.lastPressedBackspace)
            slideOutHorizontally() +
                    shrinkHorizontally() +
                    fadeOut()
        else
            slideOutVertically(targetOffsetY = { return@slideOutVertically 300 }) +
                    shrinkVertically() +
                    fadeOut()

    val zeroEnter = slideInVertically(initialOffsetY = { with(density) { AnimationMaxY.roundToPx() } }) +
            expandVertically(expandFrom = Alignment.Bottom) +
            fadeIn(initialAlpha = 0.3f)

    val zeroExit = slideOutVertically(targetOffsetY = { return@slideOutVertically 300 }) +
            shrinkVertically() +
            fadeOut()


    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x2)
                .align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center) {

                Spacer(modifier = Modifier.width(CodeTheme.dimens.staticGrid.x1))

                if (currencyResId != null && currencyResId > 0) {
                    Image(
                        modifier = Modifier
                            .requiredSize(CodeTheme.dimens.grid.x7)
                            .clip(CircleShape)
                            .align(CenterVertically),
                        painter = painterResource(
                            currencyResId
                        ),
                        contentDescription = ""
                    )
                    if (isClickable) {
                        Image(
                            modifier = Modifier
                                .width(CodeTheme.dimens.grid.x4)
                                .align(CenterVertically),
                            painter = painterResource(R.drawable.ic_dropdown),
                            contentDescription = ""
                        )
                    } else {
                        Spacer(Modifier.requiredWidth(CodeTheme.dimens.grid.x4))
                    }
                }

                Spacer(modifier = Modifier.width(CodeTheme.dimens.grid.x1))
                Text(text = amountPrefix, fontSize = textSize, fontWeight = FontWeight.Bold)

                AnimatedPlaceholderDigit(
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

                Row(
                    modifier = Modifier
                        /*.animateContentSize(
                            animationSpec = tween(
                                durationMillis = 100,
                                easing = LinearOutSlowInEasing,
                            ),
                        )*/
                ) {
                    for (i in 1 until maxDigits) {
                        Digit(
                            getComma(i),
                            GROUPING_SEPARATOR.toString(),
                            textSize,
                            density,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        )
                        Digit(
                            digitVisibility[i],
                            getValue(0, i) ?: getLastValue(0, i) ?: "",
                            textSize,
                            density
                        )
                    }

                    Digit(
                        isVisible = decimalPointVisibility,
                        text = DECIMAL_SEPARATOR.toString(),
                        fontSize = textSize,
                        density = density,
                        enter = decimalEnter,
                    )

                    for (i in 0 until totalDecimal) {
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
                }

                if (amountSuffix.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(end = CodeTheme.dimens.grid.x3),
                        text = amountSuffix,
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
        }

    }
}