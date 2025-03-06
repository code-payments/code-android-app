package xyz.flipchat.app.features.profile.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.getcode.model.social.user.SocialProfile
import com.getcode.ui.components.contextmenu.ContextMenuAction
import xyz.flipchat.app.R

sealed interface ProfileContextAction : ContextMenuAction {
    data class Labs(override val onSelect: () -> Unit) : ProfileContextAction {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.title_betaFlags)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.Science)
    }

    data class UnlinkSocialProfile(
        val profile: SocialProfile,
        override val onSelect: () -> Unit
    ) : ProfileContextAction {
        override val isDestructive: Boolean = true
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = when (profile) {
                SocialProfile.Unknown -> ""
                is SocialProfile.X -> "Disconnect X"
            }
        override val painter: Painter
            @Composable get() = when (profile) {
                SocialProfile.Unknown -> rememberVectorPainter(Icons.Outlined.LinkOff)
                is SocialProfile.X -> rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x))
            }
    }

    data class DeleteAccount(
        override val onSelect: () -> Unit
    ): ProfileContextAction {
        override val isDestructive: Boolean = true
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_deleteMyAccount)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Delete)
    }
}