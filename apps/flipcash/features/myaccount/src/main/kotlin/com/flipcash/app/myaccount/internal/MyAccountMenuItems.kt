package com.flipcash.app.myaccount.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.myaccount.R
import com.getcode.util.resources.icons.Delete

internal data object AccessKey : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_hardware_security_key)
    override val name: String
        @Composable get() = stringResource(R.string.title_accessKey)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnAccessKeyClicked
}

internal data object VerifyEmail : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.Email)
    override val name: String
        @Composable get() = stringResource(R.string.title_verifyEmailAddress)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnVerifyEmailClicked
}

internal data object VerifyPhone : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.Phone)
    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnVerifyPhoneClicked
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