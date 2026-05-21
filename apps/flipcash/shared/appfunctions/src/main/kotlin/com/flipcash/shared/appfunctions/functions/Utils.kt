package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.AppFunctionElementNotFoundException
import com.flipcash.services.user.UserManager

internal fun requireLoggedIn(userManager: UserManager) {
    if (!userManager.authState.canAccessAuthenticatedApis) {
        throw AppFunctionElementNotFoundException("User is not logged in")
    }
}
