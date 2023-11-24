package com.getcode.view

import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.*
import com.getcode.App
import com.getcode.R
import androidx.navigation.compose.composable
import com.getcode.util.AnimationUtils
import com.getcode.view.main.home.HomeScan
import timber.log.Timber


fun NavGraphBuilder.addMainGraph() {
    composable(
        route = MainSections.HOME.route,
        arguments = MainSections.HOME.arguments,
        exitTransition = { AnimationUtils.getExitTransition(this) },
        popExitTransition = { AnimationUtils.getPopExitTransition(this) },
        deepLinks = listOf(
            navDeepLink {
                uriPattern =
                    "${
                        App.getInstance().getString(R.string.root_url_app)
                    }/cash/#/e={$ARG_CASH_LINK}"
                action = Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern =
                    "${
                        App.getInstance().getString(R.string.root_url_cash)
                    }/cash/#/e={$ARG_CASH_LINK}"
                action = Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern =
                    "${App.getInstance().getString(R.string.root_url_app)}/c/#/e={$ARG_CASH_LINK}"
                action = Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern =
                    "${App.getInstance().getString(R.string.root_url_cash)}/c/#/e={$ARG_CASH_LINK}"
                action = Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern =
                    "${App.getInstance().getString(R.string.app_url_cash)}/c/#/e={$ARG_CASH_LINK}"
                action = Intent.ACTION_VIEW
            },
        )
    ) { backStackEntry ->
        val deepLink = backStackEntry.arguments?.getString(ARG_CASH_LINK).orEmpty()
        Timber.d("deeplink " + deepLink)
        HomeScan(deepLink)
    }
    composable(
        route = MainSections.LAUNCH.route,
        enterTransition = { EnterTransition.None }
    ) {
        //This composable awaits the authentication state to be complete before
        //we navigate to login or main composables.
        //We can implement an intermediate state here while the app loads if we want
        //For now lets just use an empty view so there is composition content
        ConstraintLayout(modifier = Modifier.fillMaxSize()) { }
    }
}

enum class MainSections(
    val route: String,
    val arguments: List<NamedNavArgument> = listOf(),
) {
    HOME(
        route = "main/home?${ARG_CASH_LINK}={${ARG_CASH_LINK}}",
        arguments = listOf(
            navArgument(ARG_CASH_LINK) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ),
    LAUNCH("main/launch")
}
