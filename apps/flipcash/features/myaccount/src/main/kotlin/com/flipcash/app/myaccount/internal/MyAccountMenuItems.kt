package com.flipcash.app.myaccount.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.core.R as CoreR
import com.flipcash.features.myaccount.R
import com.getcode.util.resources.icons.Delete

internal data object AccessKey : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_hardware_security_key)
    override val name: String
        @Composable get() = stringResource(R.string.title_accessKey)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnAccessKeyClicked
}

internal data object UserProfile : StaffMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.ContactMail)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_userProfile)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnContactMethodsClicked
}

internal data object LogOut : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(R.string.action_logout)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnLogOutClicked
}

internal data object DeleteAccount: FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(ImageVector.Delete)
    override val name: String
        @Composable get() = stringResource(R.string.action_deleteAccount)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnDeleteAccountClicked
}