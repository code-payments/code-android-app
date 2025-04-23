package com.flipcash.app.scanner.internal.bills

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.theme.CodeTheme
import com.getcode.ui.scanner.R
import com.getcode.ui.utils.nonScaledSp

@Composable
@Preview
fun BillAmount(modifier: Modifier = Modifier, text: String = "", flag: Int? = R.drawable.ic_flag_us) {
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
                style = CodeTheme.typography.displayLarge.copy(
                    fontSize = 40.nonScaledSp
                ),
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