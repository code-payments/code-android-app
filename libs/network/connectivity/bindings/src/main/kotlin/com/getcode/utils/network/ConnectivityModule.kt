package com.getcode.utils.network

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConnectivityModule {

    @Provides
    @SuppressLint("NewApi")
    @Singleton
    fun providesNetworkObserver(
        connectivityManager: ConnectivityManager,
        telephonyManager: TelephonyManager,
        wifiManager: WifiManager
    ): NetworkConnectivityListener = when (Build.VERSION.SDK_INT) {
        in Build.VERSION_CODES.N..Build.VERSION_CODES.P -> {
            Api24NetworkObserver(
                wifiManager,
                connectivityManager,
                telephonyManager
            )
        }

        else -> Api29NetworkObserver(
            connectivityManager,
            telephonyManager
        )
    }
}