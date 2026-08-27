package com.flipcash.app.myaccount.internal.myaccount

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.core.R as CoreR
import com.flipcash.features.myaccount.R

/**
 * Node 9277:121893. Account-shaped settings only — the destructive/diagnostic rows (Access Key,
 * Log Out, Delete Account) moved to Advanced, and the standalone App Settings screen folded its one
 * surviving toggle (Require Biometrics) in here.
 *
 * The row lands straight on the name step: [AppRoute.UpdateUserProfile] walks name, username then
 * photo, and this is only ever about the name, so it asks for that step alone.
 */
internal data object ChangeDisplayName : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Badge)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_displayName)
    override val action: MyAccountScreenViewModel.Event =
        MyAccountScreenViewModel.Event.OnChangeDisplayNameClicked
}

/**
 * Node 9491:6297. The public `@handle`. One of two ways into the same username step — the other is
 * the "You" tab's progress card — because claiming a first handle and changing an existing one are
 * the same screen, differing only in what the field is prefilled with.
 *
 * Shown only once a handle is claimed, matching iOS `SettingsMyAccountScreen`. Claiming the first
 * one belongs to the You tab's card, which carries the minimum-balance gate and disappears the
 * moment `usernameGate` reads `Claimed` — exactly where this row appears. No balance gate here: the
 * minimum exists to stop squatting at claim time, and an account holding a handle has cleared it.
 */
internal data object ChangeUsername : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.AlternateEmail)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_username)
    override val action: MyAccountScreenViewModel.Event =
        MyAccountScreenViewModel.Event.OnChangeUsernameClicked
}

/**
 * Node 9544:20116. The avatar, on its own row. [AppRoute.UpdateUserProfile] walks name then username
 * then photo, so this asks for the photo step alone — the same single-step edit the staff-only
 * profile editor already pushes.
 */
internal data object ProfilePicture : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_profile_picture)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_profilePicture)
    override val action: MyAccountScreenViewModel.Event =
        MyAccountScreenViewModel.Event.OnProfilePictureClicked
}

/**
 * A toggle, not a destination — the screen renders a switch in its trailing slot and routes the tap
 * through a biometric prompt. Its [action] is what a row tap dispatches, same as the switch.
 */
internal data object RequireBiometrics : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_biometrics)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_requireBiometrics)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnBiometricsToggled
}

internal data object Blocklist : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Block)
    override val name: String
        @Composable get() = stringResource(R.string.title_blocklist)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnBlocklistClicked
}

/**
 * Staff/beta only: the whole profile editor — contact methods, photo, name. What it adds over the
 * rows above is the contact methods; the name and the photo are already reachable by everyone
 * through [ChangeDisplayName] and [ProfilePicture].
 */
internal data object UserProfile : StaffMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.ContactMail)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_userProfile)
    override val action: MyAccountScreenViewModel.Event =
        MyAccountScreenViewModel.Event.OnContactMethodsClicked
}
