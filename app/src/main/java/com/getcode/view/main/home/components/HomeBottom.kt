package com.getcode.view.main.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.view.main.home.HomeBottomSheet

@Preview
@Composable
internal fun HomeBottom(
    modifier: Modifier = Modifier,
    onPress: (homeBottomSheet: HomeBottomSheet) -> Unit = {},
) {
    ConstraintLayout(modifier = modifier.fillMaxWidth()) {
        val (button1, button2, button3) = createRefs()

        Column(modifier = Modifier
            .constrainAs(button2) {
                centerHorizontallyTo(parent)
                bottom.linkTo(parent.bottom)
            }
            .clip(
                RoundedCornerShape(10.dp)
            )
            .clickable { onPress(HomeBottomSheet.GIVE_KIN) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .padding(vertical = 11.dp)
                    .height(51.dp)
                    .width(51.dp),
                painter = painterResource(
                    R.drawable.ic_kin_white
                ),
                contentDescription = stringResource(R.string.action_giveKin),
            )
            Text(
                text = stringResource(R.string.action_giveKin),
                style = CodeTheme.typography.body2
            )
        }

        Column(modifier = Modifier
            .constrainAs(button1) {
                start.linkTo(parent.start)
                bottom.linkTo(parent.bottom)
            }
            .padding(start = 15.dp)
            .clip(
                RoundedCornerShape(10.dp, 10.dp, 10.dp, 10.dp)
            )
            .clickable {
                onPress(HomeBottomSheet.GET_KIN)
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 8.dp, top = 5.dp)
                    .height(32.dp)
                    .width(32.dp),
                painter = painterResource(
                    R.drawable.ic_wallet
                ),
                contentDescription = stringResource(R.string.title_getKin),
            )
            Text(
                text = stringResource(R.string.title_getKin),
                style = CodeTheme.typography.body2
            )
        }

        Column(modifier = Modifier
            .constrainAs(button3) {
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            }
            .padding(end = 15.dp)
            .clip(
                RoundedCornerShape(10.dp, 10.dp, 10.dp, 10.dp)
            )
            .clickable { onPress(HomeBottomSheet.BALANCE) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .height(44.dp)
                    .width(44.dp),
                painter = painterResource(
                    R.drawable.ic_history
                ),
                contentDescription = stringResource(R.string.action_balance),
            )
            Text(
                text = stringResource(R.string.action_balance),
                style = CodeTheme.typography.body2
            )
        }
    }
}