package com.flipcash.app.myaccount.internal.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.core.R as CoreR
import com.flipcash.features.myaccount.R
import com.getcode.util.resources.icons.Delete

internal data object AccessKey : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_hardware_security_key)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_accessKey)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OnAccessKeyClicked
}

/**
 * A toggle, not a destination — the screen renders a switch in its trailing slot and routes the tap
 * through a biometric prompt. Its [action] is what a row tap dispatches, same as the switch.
 */
internal data object RequireBiometrics : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_biometrics)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_requireBiometrics)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OnBiometricsToggled
}

internal data object Blocklist : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Block)
    override val name: String
        @Composable get() = stringResource(R.string.title_blocklist)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenScreen(AppRoute.Menu.Blocklist)
}

/** Always shown; the screen has its own empty state. */
internal data object TrustedWebsites : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Language)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_trustedWebsites)
    override val action: SettingsViewModel.Event =
        SettingsViewModel.Event.OpenScreen(AppRoute.Menu.TrustedWebsites)
}

internal data object BillCustomizer : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Palette)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_billCustomizer)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenBillPlayground
}

internal data object DeviceLogs : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Description)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_deviceLogs)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenScreen(AppRoute.Menu.DeviceLogs)
}

internal data object BetaFlags : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_betaFlags)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenScreen(AppRoute.Menu.Lab())
}

/** Staff/beta only; the switcher is still a beta tool, so it carries the beta badge. */
internal data object SwitchAccount : StaffMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_switchAccounts)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenScreen(AppRoute.Menu.AccountSelection)
}

/**
 * Staff/beta only: the full account screen — contact methods, public key and the rest of the
 * account's identifiers.
 */
internal data object AccountInfo : StaffMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.ContactMail)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_sectionAccountInfo)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OpenScreen(AppRoute.Menu.UserProfile)
}

internal data object LogOut : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(CoreR.string.action_logout)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OnLogOutClicked
}

internal data object DeleteAccount : FullMenuItem<SettingsViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(ImageVector.Delete)
    override val name: String
        @Composable get() = stringResource(CoreR.string.action_deleteAccount)
    override val action: SettingsViewModel.Event = SettingsViewModel.Event.OnDeleteAccountClicked
}
