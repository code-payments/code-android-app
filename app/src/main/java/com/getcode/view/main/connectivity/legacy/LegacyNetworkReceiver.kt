package com.getcode.view.main.connectivity.legacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


class LegacyNetworkReceiver: BroadcastReceiver() {

    val legacyFlow = MutableStateFlow(true)

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { legacyFlow.value = checkInternet(context = it) }
    }

    fun checkInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}