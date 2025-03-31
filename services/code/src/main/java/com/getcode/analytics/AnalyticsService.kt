package com.getcode.analytics

import com.getcode.libs.analytics.AnalyticsService
import com.getcode.services.model.AppSetting
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.solana.keys.PublicKey

interface CodeAnalyticsService : AnalyticsService {
    fun login(ownerPublicKey: String, autoCompleteCount: Int, inputChangeCount: Int)
    fun logout()
    fun createAccount(isSuccessful: Boolean, ownerPublicKey: String?)
    fun billTimeoutReached(
        kin: Kin,
        currencyCode: CurrencyCode,
        animation: CodeAnalyticsManager.BillPresentationStyle
    )

    fun billShown(
        kin: Kin,
        currencyCode: CurrencyCode,
        animation: CodeAnalyticsManager.BillPresentationStyle
    )

    fun billHidden(
        kin: Kin,
        currencyCode: CurrencyCode,
        animation: CodeAnalyticsManager.BillPresentationStyle
    )

    fun transfer(amount: KinAmount, successful: Boolean)
    fun transferForRequest(amount: KinAmount, successful: Boolean)
    fun transferForTip(amount: KinAmount, successful: Boolean)
    fun remoteSendOutgoing(kin: Kin, currencyCode: CurrencyCode)
    fun remoteSendIncoming(kin: Kin, currencyCode: CurrencyCode, isVoiding: Boolean)
    fun recomputed(fxIn: Double, fxOut: Double)
    fun grabStart()
    fun grab(kin: Kin, currencyCode: CurrencyCode)
    fun requestShown(amount: KinAmount)
    fun requestHidden(amount: KinAmount)
    fun cashLinkGrabStart()
    fun cashLinkGrab(kin: Kin, currencyCode: CurrencyCode)
    fun migration(amount: Kin)
    fun upgradePrivacy(successful: Boolean, intentId: PublicKey, actionCount: Int)
    fun onBillReceived()
    fun withdrawal(amount: KinAmount, successful: Boolean)

    fun tipCardShown(username: String)
    fun tipCardLinked()

    fun backgroundSwapInitiated()

    fun appSettingToggled(setting: AppSetting, value: Boolean)

    fun photoScanned(successful: Boolean, timeToScanInMillis: Long)
}