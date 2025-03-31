package xyz.flipchat.app.features.chat.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.extensions.formattedRaw
import com.getcode.model.KinAmount
import com.getcode.model.chat.Sender
import com.getcode.model.sum
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.user.social.SenderNameDisplay
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty

internal data class MessageTipsSheet(val tips: List<MessageTip>) : Screen {

    @Composable
    override fun Content() {
        val userTips: List<Pair<Sender, KinAmount>> = remember {
            tips.groupBy { it.tipper }
                .mapValues { it.value.map { it.amount }.sum() }
                .toList().sortedByDescending { it.second.fiat }
        }

        val imageModifier = Modifier
            .size(CodeTheme.dimens.staticGrid.x8)
            .clip(CircleShape)


        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = CodeTheme.dimens.inset),
                text = "Tips",
                style = CodeTheme.typography.screenTitle
            )
            LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                items(userTips) { (tipper, amount) ->
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            modifier = imageModifier,
                            data = tipper.profileImage.nullIfEmpty() ?: tipper.id
                        ) {
                            Image(
                                modifier = Modifier.padding(5.dp),
                                imageVector = Icons.Default.Person,
                                colorFilter = ColorFilter.tint(Color.White),
                                contentDescription = null,
                            )
                        }
                        SenderNameDisplay(
                            sender = tipper,
                            textStyle = CodeTheme.typography.textMedium,
                            textColor = CodeTheme.colors.onSurface
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = stringResource(
                                R.string.title_kinAmountWithLogo,
                                amount.formattedRaw()
                            ),
                            color = CodeTheme.colors.onSurface,
                            style = CodeTheme.typography.textMedium,
                        )
                    }
                }
            }
        }
    }
}