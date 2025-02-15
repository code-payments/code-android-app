package com.getcode.ui.components.user.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MinimalMember
import com.getcode.model.chat.Sender
import com.getcode.model.social.user.SocialProfile
import com.getcode.model.social.user.XExtraData
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun SenderNameDisplay(
    sender: Sender,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = CodeTheme.typography.caption,
    textColor: Color = CodeTheme.colors.tertiary
) {
    val socialProfile = remember(sender.socialProfiles) {
        sender.socialProfiles.firstOrNull()
    }

    SocialUserDisplay(
        modifier = modifier,
        nameSlot = {
            Text(
                text = sender.displayName.orEmpty(),
                style = textStyle,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        startSlot = null,
        endSlot = socialProfile?.let {
            {
                when (it.platformType) {
                    "x" -> {
                        val metadata = it.metadata<XExtraData>()
                        metadata?.verificationType?.let { type ->
                            type.checkmark()?.let { asset ->
                                Image(
                                    modifier = Modifier.size(16.dp),
                                    painter = rememberVectorPainter(image = asset),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    )
}

@Composable
fun MemberNameDisplay(
    member: MinimalMember,
    modifier: Modifier = Modifier,
) {
    val socialProfile = remember(member.socialProfiles) {
        member.socialProfiles.firstOrNull()
    }

    SocialUserDisplay(
        modifier = modifier,
        startSlot = null,
        nameSlot = {
            Text(
                text = when {
                    member.isSelf -> stringResource(R.string.subtitle_you)
                    else -> member.displayName ?: stringResource(R.string.subtitle_listener)
                },
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        endSlot = if (member.isSelf) null else socialProfile?.let {
            {
                when (it.platformType) {
                    "x" -> {
                        val metadata = it.metadata<XExtraData>()
                        metadata?.verificationType?.let { type ->
                            type.checkmark()?.let { asset ->
                                Image(
                                    modifier = Modifier.size(16.dp),
                                    painter = rememberVectorPainter(image = asset),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    )
}

@Composable
fun SocialUserDisplay(
    profile: SocialProfile,
    modifier: Modifier = Modifier,
) {
    SocialUserDisplay(
        modifier = modifier,
        nameSlot = {
            Text(
                text = when (profile) {
                    SocialProfile.Unknown -> ""
                    is SocialProfile.X -> profile.friendlyName
                },
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
        },
        startSlot = profile.takeIf { it !is SocialProfile.Unknown }?.let {
            {
                when (profile) {
                    is SocialProfile.X -> {
                        Image(
                            painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
                            contentDescription = null
                        )
                    }

                    SocialProfile.Unknown -> Unit
                }
            }
        },
        endSlot = profile.takeIf { it !is SocialProfile.Unknown }?.let {
            {
                when (profile) {
                    is SocialProfile.X -> {
                        profile.verificationType.let { type ->
                            type.checkmark()?.let { asset ->
                                Image(
                                    painter = rememberVectorPainter(image = asset),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    SocialProfile.Unknown -> Unit
                }
            }
        }
    )
}

@Composable
fun SocialUserDisplay(
    nameSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    startSlot: (@Composable () -> Unit)? = null,
    endSlot: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x1,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startSlot?.invoke()
        Box(modifier = Modifier.weight(1f, fill = false)) { nameSlot() }
        endSlot?.invoke()
    }
}

@Composable
fun SocialProfile.X.VerificationType.checkmark(): ImageVector? {
    return when (this) {
        SocialProfile.X.VerificationType.BLUE -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge)
        SocialProfile.X.VerificationType.BUSINESS -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gold)
        SocialProfile.X.VerificationType.GOVERNMENT -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gray)
        else -> null
    }
}