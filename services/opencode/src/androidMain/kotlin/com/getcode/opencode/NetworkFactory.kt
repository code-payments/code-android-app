package com.getcode.opencode

import android.content.Context
import com.getcode.utils.network.ConnectivityModule
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.hilt.android.EntryPointAccessors

object NetworkFactory {
    fun createNetworkObserver(context: Context): NetworkConnectivityListener {
        val appContext = context.applicationContext ?: throw IllegalStateException(
            "applicationContext was not provided",
        )

        val connectivityModule = EntryPointAccessors.fromApplication(
            appContext,
            ConnectivityModule::class.java,
        )

        val telephony = connectivityModule.providesTelephonyManager(context)
        val connectivity = connectivityModule.providesConnectivityManager(context)
        val wifi = connectivityModule.providesWifiManager(context)

        return connectivityModule.providesNetworkObserver(
            connectivity, telephony, wifi
        )
    }
}