package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.contacts.ui.ContactAvatar
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme

@Composable
internal fun ContactInfoContainer(
    contact: DeviceContact?,
    modifier: Modifier = Modifier,
    onRefreshContact: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .border(
                color = CodeTheme.colors.divider,
                width = CodeTheme.dimens.border,
                shape = CodeTheme.shapes.medium,
            )
            .padding(CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactAvatar(
            contact = contact,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = contact?.displayName.orEmpty(),
            autoSize = TextAutoSize.StepBased(
                minFontSize = CodeTheme.typography.textSmall.fontSize,
                maxFontSize = CodeTheme.typography.textLarge.fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        if (contact?.isUnknown == false) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = contact.displayNumber,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { onRefreshContact() }

        ContactPill(
            modifier = Modifier.padding(top = CodeTheme.dimens.inset),
            contact = contact
        ) {
            contact ?: return@ContactPill
            val intent = IntentUtils.openContact(contact).apply {
                // Remove NEW_TASK so the result callback fires when the user returns,
                // not immediately.
                flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            }
            launcher.launch(intent)
        }
    }
}

@Composable
private fun ContactPill(
    contact: DeviceContact?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedContent(contact) { c ->
        if (c == null) {
            Spacer(modifier = modifier.fillMaxWidth())
            return@AnimatedContent
        }

        val backgroundColor by animateColorAsState(
            if (!c.isUnknown) CodeTheme.colors.surfaceVariant else CodeTheme.colors.warning.copy(alpha = 0.10f)
        )

        val contentColor by animateColorAsState(
            if (!c.isUnknown) CodeTheme.colors.textSecondary else CodeTheme.colors.warning
        )

        Row(
            modifier = modifier
                .background(color = backgroundColor, shape = CircleShape)
                .clip(CircleShape)
                .clickable { onClick() }
                .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(
                    if (!c.isUnknown) {
                        R.drawable.ic_existing_contact
                    } else {
                        R.drawable.ic_unknown_contact
                    }
                ),
                contentDescription = null,
                tint = contentColor,
            )

            Text(
                text = if (!c.isUnknown) stringResource(R.string.label_fromYourContacts) else stringResource(R.string.label_addContact),
                color = contentColor,
                style = CodeTheme.typography.textSmall,
            )
        }
    }
}