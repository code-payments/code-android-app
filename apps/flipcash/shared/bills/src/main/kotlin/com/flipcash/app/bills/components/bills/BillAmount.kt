package com.flipcash.app.bills.components.bills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.flipcash.shared.bills.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.nonScaledSp

@Composable
internal fun BillAmount(
    modifier: Modifier = Modifier,
    text: String = "",
    flag: Int? = R.drawable.ic_flag_us,
    textStyle: TextStyle = CodeTheme.typography.displayLarge.copy(
        fontWeight = FontWeight.W600,
        fontSize = 50.nonScaledSp
    )
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .vertical()
                .rotate(-90f)
        ) {
//            if (flag != null) {
//                Image(
//                    modifier = Modifier
//                        .padding(end = CodeTheme.dimens.grid.x2)
//                        .height(CodeTheme.dimens.grid.x5)
//                        .width(CodeTheme.dimens.grid.x5)
//                        .align(Alignment.CenterVertically)
//                        .clip(CircleShape),
//                    painter = painterResource(id = flag),
//                    contentDescription = ""
//                )
//            }
            Text(
                text = text,
                style = textStyle,
                color = CodeTheme.colors.onBackground
            )
        }
    }
}

fun Modifier.vertical() =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }