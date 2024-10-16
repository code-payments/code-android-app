package com.getcode.network.repository

import io.reactivex.rxjava3.core.Single
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class StatusRepository {
    fun getIsUpgradeRequired(currentVersionCode: Int): Single<Boolean> {
        val request: Request = Request.Builder()
            .url("https://app.getcode.com/status")
            .build()
        val call: Call = OkHttpClient().newCall(request)

        return Single.create {
            val response: Response = call.execute()
            if (!response.isSuccessful) {
                it.onError(Exception())
                return@create
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val minimumVersion = json.getInt("minimumClientVersion")
            val isUpgradeRequired = currentVersionCode < minimumVersion
            it.onSuccess(isUpgradeRequired)
        }
    }
}