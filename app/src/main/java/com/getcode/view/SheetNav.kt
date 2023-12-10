package com.getcode.view

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.navigation.*
import com.getcode.R
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.main.account.*
import com.getcode.view.main.account.AccountPage.*
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.withdraw.AccountWithdrawAddress
import com.getcode.view.main.account.withdraw.AccountWithdrawAmount
import com.getcode.view.main.account.withdraw.AccountWithdrawSummary
import com.getcode.view.main.getKin.BuyAndSellKin
import com.getcode.view.main.getKin.GetKin
import com.getcode.view.main.getKin.ReferFriend
import com.getcode.view.main.home.HomeViewModel


@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.addSheetGraph(
    navController: NavController,
    homeViewModel: HomeViewModel,
    onTitleChange: (Int?) -> Unit,
    onBackButtonVisibilityChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val onAccountHomeNav = { page: AccountPage ->
        when (page) {
            BUY_AND_SELL_KIN -> navController.navigate(SheetSections.BUY_AND_SELL_KIN.route)
            DEPOSIT -> navController.navigate(SheetSections.DEPOSIT.route)
            WITHDRAW -> navController.navigate(SheetSections.WITHDRAW_AMOUNT.route)
            ACCESS_KEY -> navController.navigate(SheetSections.ACCESS_KEY.route)
            FAQ -> navController.navigate(SheetSections.FAQ.route)
            ACCOUNT_DETAILS -> navController.navigate(SheetSections.ACCOUNT_DETAILS.route)
            PHONE -> navController.navigate(
                SheetSections.PHONE.route
                    .replace("{${ARG_IS_PHONE_LINKING}}", true.toString())
            )
            ACCOUNT_DEBUG_OPTIONS -> navController.navigate(SheetSections.ACCOUNT_DEBUG_OPTIONS.route)
        }
    }

    composableItem(SheetSections.NONE.route) {}
    composableItem(
        route = SheetSections.HOME.route
    ) {
        onBackButtonVisibilityChange(false)
        onTitleChange(null)
        AccountHome(onAccountHomeNav)
    }
    composableItem(
        route = SheetSections.DEPOSIT.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.DEPOSIT.title)
        AccountDeposit()
    }
    composableItem(
        route = SheetSections.WITHDRAW_AMOUNT.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.WITHDRAW_AMOUNT.title)
        AccountWithdrawAmount(navController)
    }
    composableItem(
        route = SheetSections.WITHDRAW_ADDRESS.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.WITHDRAW_ADDRESS.title)
        AccountWithdrawAddress(navController, it.arguments)
    }
    composableItem(
        route = SheetSections.WITHDRAW_SUMMARY.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.WITHDRAW_SUMMARY.title)
        AccountWithdrawSummary(navController, it.arguments, onClose)
    } 
    composableItem(
        route = SheetSections.ACCESS_KEY.route
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.ACCESS_KEY.title)
        AccountAccessKey(navController)
    }
    composableItem(
        route = SheetSections.FAQ.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.FAQ.title)
        AccountFaq()
    }

    composableItem(
        route = SheetSections.PHONE.route,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.PHONE.title)
        AccountPhone(navController)
    }
    composableItem(
        route = SheetSections.PHONE_VERIFY.route,
        arguments = SheetSections.PHONE_VERIFY.arguments,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.PHONE_VERIFY.title)
        PhoneVerify(navController, it.arguments)
    }
    composableItem(
        route = SheetSections.PHONE_CONFIRM.route,
        arguments = SheetSections.PHONE_CONFIRM.arguments,
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.PHONE_CONFIRM.title)
        PhoneConfirm(navController, it.arguments)
    }
    composableItem(
        route = SheetSections.ACCOUNT_DETAILS.route,
        arguments = SheetSections.ACCOUNT_DETAILS.arguments
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.ACCOUNT_DETAILS.title)
        AccountDetails(onAccountHomeNav)
    }
    composableItem(
        route = SheetSections.ACCOUNT_DEBUG_OPTIONS.route
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(SheetSections.ACCOUNT_DEBUG_OPTIONS.title)
        AccountDebugOptions()
    }
    composableItem(
        route = SheetSections.GET_KIN.route
    ) {
        onBackButtonVisibilityChange(false)
        onTitleChange(R.string.empty)
        GetKin(onClose, navController, homeViewModel)
    }
    composableItem(
        route = SheetSections.REFER_FRIEND.route

    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(R.string.empty)
        ReferFriend(onClose, navController)
    }

    composableItem(
        route = SheetSections.BUY_AND_SELL_KIN.route
    ) {
        onBackButtonVisibilityChange(true)
        onTitleChange(R.string.empty)
        BuyAndSellKin()
    }
}

enum class SheetSections(
    @StringRes val title: Int?,
    val route: String,
    val arguments: List<NamedNavArgument> = listOf(),
) {
    NONE(
        title = null,
        "sheet/none"
    ),
    HOME(
        title = null,
        route = "sheet/account"
    ),
    DEPOSIT(
        title = R.string.title_depositKin,
        route = "sheet/deposit"
    ),
    WITHDRAW_AMOUNT(
        title = R.string.title_withdrawKin,
        route = "sheet/withdraw-amount"
    ),
    WITHDRAW_ADDRESS(
        title = R.string.title_withdrawKin,
        route = "sheet/withdraw-address?" +
                "${ARG_WITHDRAW_AMOUNT_FIAT}={${ARG_WITHDRAW_AMOUNT_FIAT}}&" +
                "${ARG_WITHDRAW_AMOUNT_KIN}={${ARG_WITHDRAW_AMOUNT_KIN}}&" +
                "${ARG_WITHDRAW_AMOUNT_TEXT}={${ARG_WITHDRAW_AMOUNT_TEXT}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_CODE}={${ARG_WITHDRAW_AMOUNT_CURRENCY_CODE}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID}={${ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_RATE}={${ARG_WITHDRAW_AMOUNT_CURRENCY_RATE}}",
        arguments = listOf(
            navArgument(ARG_WITHDRAW_AMOUNT_FIAT) {
                type = NavType.FloatType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_KIN) {
                type = NavType.LongType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_TEXT) {
                type = NavType.StringType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_CODE) {
                type = NavType.StringType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID) {
                type = NavType.IntType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_RATE) {
                type = NavType.FloatType
            },
        )
    ),
    WITHDRAW_SUMMARY(
        title = R.string.title_withdrawKin,
        route = "sheet/withdraw-summary?" +
                "${ARG_WITHDRAW_AMOUNT_FIAT}={${ARG_WITHDRAW_AMOUNT_FIAT}}&" +
                "${ARG_WITHDRAW_AMOUNT_KIN}={${ARG_WITHDRAW_AMOUNT_KIN}}&" +
                "${ARG_WITHDRAW_AMOUNT_TEXT}={${ARG_WITHDRAW_AMOUNT_TEXT}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_CODE}={${ARG_WITHDRAW_AMOUNT_CURRENCY_CODE}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID}={${ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID}}&" +
                "${ARG_WITHDRAW_AMOUNT_CURRENCY_RATE}={${ARG_WITHDRAW_AMOUNT_CURRENCY_RATE}}&" +
                "${ARG_WITHDRAW_ADDRESS}={${ARG_WITHDRAW_ADDRESS}}&" +
                "${ARG_WITHDRAW_RESOLVED_DESTINATION}={${ARG_WITHDRAW_RESOLVED_DESTINATION}}",
        arguments = listOf(
            navArgument(ARG_WITHDRAW_AMOUNT_FIAT) {
                type = NavType.FloatType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_KIN) {
                type = NavType.LongType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_TEXT) {
                type = NavType.StringType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_CODE) {
                type = NavType.StringType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID) {
                type = NavType.IntType
            },
            navArgument(ARG_WITHDRAW_AMOUNT_CURRENCY_RATE) {
                type = NavType.FloatType
            },
            navArgument(ARG_WITHDRAW_ADDRESS) {
                type = NavType.StringType
            })
    ),
    ACCESS_KEY(
        title = R.string.title_accessKey,
        route = "sheet/recovery"
    ),
    FAQ(
        title = R.string.title_faq,
        route = "sheet/faq"
    ),
    PHONE(
        title = R.string.title_phoneNumber,
        route = "sheet/phone"
    ),
    ACCOUNT_DETAILS(
        title = R.string.title_myAccount,
        route = "sheet/accountDetails"
    ),
    PHONE_VERIFY(
        title = R.string.title_enterPhoneNumber,
        route = "login/verifyPhone?" +
                "${ARG_SIGN_IN_ENTROPY_B64}={${ARG_SIGN_IN_ENTROPY_B64}}&" +
                "${ARG_IS_PHONE_LINKING}={${ARG_IS_PHONE_LINKING}}",
        arguments = listOf(
            navArgument(ARG_SIGN_IN_ENTROPY_B64) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(ARG_IS_PHONE_LINKING) {
                type = NavType.BoolType
                defaultValue = false
            })
    ),
    PHONE_CONFIRM(
        title = R.string.title_verifyPhoneNumber,
        route = "login/confirmPhone?" +
                "${ARG_PHONE_NUMBER}={${ARG_PHONE_NUMBER}}&" +
                "${ARG_SIGN_IN_ENTROPY_B64}={${ARG_SIGN_IN_ENTROPY_B64}}&" +
                "${ARG_IS_PHONE_LINKING}={${ARG_IS_PHONE_LINKING}}",
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
            }
        ),
    ),
    ACCOUNT_DEBUG_OPTIONS(
        title = R.string.account_debug_options,
        route = "sheet/account-debug-options"
    ),
    GET_KIN(
        title = R.string.title_getKin,
        route = "sheet/get-kin"
    ),
    REFER_FRIEND(
        title = R.string.title_getFriendStartedOnCode,
        route = "sheet/refer-friend"
    ),
    BUY_AND_SELL_KIN(
        title = R.string.title_buyAndSellKin,
        route = "sheet/buy-and-sell-kin"
    ),
}