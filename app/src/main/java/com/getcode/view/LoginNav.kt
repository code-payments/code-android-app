package com.getcode.view

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.getcode.App
import com.getcode.util.AnimationUtils
import com.getcode.view.login.*
import com.getcode.R
import timber.log.Timber


fun NavGraphBuilder.composableItem(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterTransition ?: { AnimationUtils.getEnterTransition(this) },
        exitTransition = { AnimationUtils.getExitTransition(this) },
        popEnterTransition = { AnimationUtils.getPopEnterTransition(this) },
        popExitTransition = { AnimationUtils.getPopExitTransition(this) },
        content = content,
    )
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.addLoginGraph(navController: NavController, upPress: () -> Unit) {
//    composableItem(
//        route = LoginSections.LOGIN.route,
//        enterTransition = null,
//    ) {
//        LoginHome(navController, upPress)
//    }
//    composableItem(
//        route = LoginSections.PHONE_VERIFY.route,
//        arguments = LoginSections.PHONE_VERIFY.arguments
//    ) { backStackEntry ->
//        PhoneVerify(navController, backStackEntry.arguments)
//    }
//    composableItem(
//        LoginSections.PHONE_CONFIRM.route,
//        LoginSections.PHONE_CONFIRM.arguments,
//    ) { backStackEntry ->
//        PhoneConfirm(navController, backStackEntry.arguments)
//    }
//    composableItem(
//        LoginSections.INVITE_CODE.route,
//        LoginSections.INVITE_CODE.arguments,
//    ) { backStackEntry ->
//        InviteCode(navController, backStackEntry.arguments)
//    }
//    composableItem(
//        route = LoginSections.SEED_INPUT.route,
//        arguments = LoginSections.SEED_INPUT.arguments,
//    ) {
//        SeedInput(navController)
//    }
//    composableItem(
//        route = LoginSections.SEED_VIEW.route,
//        arguments = LoginSections.SEED_VIEW.arguments,
//    ) { backStackEntry ->
//        AccessKey(navController, upPress, backStackEntry.arguments)
//    }
//    composableItem(
//        route = LoginSections.SEED_DEEP_LINK.route,
//        arguments = LoginSections.SEED_DEEP_LINK.arguments,
//        deepLinks = listOf(
//            //New
//            navDeepLink {
//                uriPattern =
//                    "${
//                        App.getInstance().getString(R.string.root_url_app)
//                    }/login/#/e={$ARG_SIGN_IN_ENTROPY_B58}"
//            },
//            //Legacy
//            navDeepLink {
//                uriPattern =
//                    "${
//                        App.getInstance().getString(R.string.root_url_app)
//                    }/login?data={$ARG_SIGN_IN_ENTROPY_B58}"
//            }
//        )
//    ) { backStackEntry ->
//        SeedDeepLink(navController, backStackEntry.arguments)
//    }
//    composableItem(
//        route = LoginSections.PERMISSION_CAMERA_REQUEST.route,
//        arguments = LoginSections.PERMISSION_CAMERA_REQUEST.arguments,
//    ) {
//        CameraPermission(navController)
//    }
//    composableItem(
//        route = LoginSections.PERMISSION_NOTIFICATION_REQUEST.route,
//        arguments = LoginSections.PERMISSION_NOTIFICATION_REQUEST.arguments,
//    ) {
//        NotificationPermission(navController)
//    }
}

enum class LoginSections(
    @StringRes val title: Int?,
    val route: String,
    val arguments: List<NamedNavArgument> = listOf(),
) {
    LOGIN(
        title = R.string.action_logIn,
        route = "login/login"
    ),
    PHONE_VERIFY(
        title = R.string.title_enterPhoneNumber,
        route = "login/verifyPhone?" +
                "${ARG_SIGN_IN_ENTROPY_B64}={${ARG_SIGN_IN_ENTROPY_B64}}&" +
                "${ARG_IS_PHONE_LINKING}={${ARG_IS_PHONE_LINKING}}&" +
                "${ARG_IS_NEW_ACCOUNT}={${ARG_IS_NEW_ACCOUNT}}",
        arguments = listOf(
            navArgument(ARG_SIGN_IN_ENTROPY_B64) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(ARG_IS_PHONE_LINKING) {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument(ARG_IS_NEW_ACCOUNT) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ),
//    INVITE_CODE(
//        title = R.string.subtitle_inviteCode,
//        route = "login/inviteCode?" +
//                "${ARG_PHONE_NUMBER}={${ARG_PHONE_NUMBER}}&",
//        arguments = listOf(
//            navArgument(ARG_PHONE_NUMBER) {
//                type = NavType.StringType
//            }
//        )
//    ),
    PHONE_CONFIRM(
        title = R.string.title_verifyPhoneNumber,
        route = "login/confirmPhone?" +
                "${ARG_PHONE_NUMBER}={${ARG_PHONE_NUMBER}}&" +
                "${ARG_SIGN_IN_ENTROPY_B64}={${ARG_SIGN_IN_ENTROPY_B64}}&" +
                "${ARG_IS_PHONE_LINKING}={${ARG_IS_PHONE_LINKING}}&" +
                "${ARG_IS_NEW_ACCOUNT}={${ARG_IS_NEW_ACCOUNT}}",
        arguments = listOf(
            navArgument(ARG_PHONE_NUMBER) {
                type = NavType.StringType
            },
            navArgument(ARG_SIGN_IN_ENTROPY_B64) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(ARG_IS_PHONE_LINKING) {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument(ARG_IS_NEW_ACCOUNT) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ),
//    SEED_INPUT(
//        title = R.string.title_enterAccessKeyWords,
//        route = "login/inputSeed"
//    ),
    SEED_VIEW(
        title = R.string.title_accessKey,
        route = "login/viewSeed?" +
                "${ARG_SIGN_IN_ENTROPY_B64}={${ARG_SIGN_IN_ENTROPY_B64}}",
        arguments = listOf(
            navArgument(ARG_SIGN_IN_ENTROPY_B64) {
                type = NavType.StringType
            }
        )
    ),
    SEED_DEEP_LINK(
        title = null,
        route = "login/seedDeepLink?" +
                "${ARG_SIGN_IN_ENTROPY_B58}={${ARG_SIGN_IN_ENTROPY_B58}}",
        arguments = listOf(
            navArgument(ARG_SIGN_IN_ENTROPY_B58) {
                type = NavType.StringType
            }
        )
    ),
    PERMISSION_CAMERA_REQUEST(
        title = null,
        route = "login/permissionRequest"
    ),
    PERMISSION_NOTIFICATION_REQUEST(
        title = null,
        route = "login/permissionNotificationRequest"
    )
}

const val ARG_SIGN_IN_ENTROPY_B64 = "signInEntropyB64"
const val ARG_SIGN_IN_ENTROPY_B58 = "signInEntropyB58"
const val ARG_CASH_LINK = "cashLink"
const val ARG_PHONE_NUMBER = "phoneNumber"
const val ARG_IS_PHONE_LINKING = "isPhoneLinking"
const val ARG_IS_NEW_ACCOUNT = "isNewAccount"

const val ARG_WITHDRAW_AMOUNT_FIAT = "withdrawAmountFiat"
const val ARG_WITHDRAW_AMOUNT_KIN = "withdrawAmountKin"
const val ARG_WITHDRAW_AMOUNT_TEXT = "withdrawAmountText"

const val ARG_WITHDRAW_AMOUNT_CURRENCY_CODE = "withdrawAmountCurrencyCode"
const val ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID = "withdrawAmountCurrencyResId"
const val ARG_WITHDRAW_AMOUNT_CURRENCY_RATE = "withdrawAmountCurrencyRate"

const val ARG_WITHDRAW_ADDRESS = "withdrawAddress"
const val ARG_WITHDRAW_RESOLVED_DESTINATION = "resolvedDestination"