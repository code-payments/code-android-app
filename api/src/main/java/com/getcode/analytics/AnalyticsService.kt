package com.getcode.analytics

import com.getcode.model.AppSetting
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.solana.keys.PublicKey

interface AnalyticsService {
    fun onAppStart()
    fun onAppStarted()
    fun login(ownerPublicKey: String, autoCompleteCount: Int, inputChangeCount: Int)
    fun logout()
    fun createAccount(isSuccessful: Boolean, ownerPublicKey: String?)
    fun billTimeoutReached(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    )
    fun billShown(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    )
    fun billHidden(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    )
    fun transfer(amount: KinAmount, successful: Boolean)
    fun transferForRequest(amount: KinAmount, successful: Boolean)
    fun transferForTip(amount: KinAmount, successful: Boolean)
    fun remoteSendOutgoing(kin: Kin, currencyCode: com.getcode.model.CurrencyCode)
    fun remoteSendIncoming(kin: Kin, currencyCode: com.getcode.model.CurrencyCode, isVoiding: Boolean)
    fun recomputed(fxIn: Double, fxOut: Double)
    fun grabStart()
    fun grab(kin: Kin, currencyCode: com.getcode.model.CurrencyCode)
    fun requestShown(amount: KinAmount)
    fun requestHidden(amount: KinAmount)
    fun cashLinkGrabStart()
    fun cashLinkGrab(kin: Kin, currencyCode: com.getcode.model.CurrencyCode)
    fun migration(amount: Kin)
    fun upgradePrivacy(successful: Boolean, intentId: PublicKey, actionCount: Int)
    fun onBillReceived()
    fun withdrawal(amount: KinAmount, successful: Boolean)

    fun tipCardShown(username: String)
    fun tipCardLinked()

    fun backgroundSwapInitiated()
    fun unintentionalLogout()

    fun appSettingToggled(setting: AppSetting, value: Boolean)

    fun photoScanned(successful: Boolean, timeToScanInMillis: Long)

    fun action(action: Action, source: ActionSource? = null)
}

class AnalyticsServiceNull : AnalyticsService {
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun login(ownerPublicKey: String, autoCompleteCount: Int, inputChangeCount: Int) = Unit
    override fun logout() = Unit
    override fun createAccount(isSuccessful: Boolean, ownerPublicKey: String?) = Unit
    override fun billTimeoutReached(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    ) = Unit
    override fun billShown(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    ) = Unit
    override fun billHidden(
        kin: Kin,
        currencyCode: com.getcode.model.CurrencyCode,
        animation: AnalyticsManager.BillPresentationStyle
    ) = Unit
    override fun transfer(amount: KinAmount, successful: Boolean) = Unit
    override fun transferForRequest(amount: KinAmount, successful: Boolean) = Unit
    override fun transferForTip(amount: KinAmount, successful: Boolean) = Unit
    override fun withdrawal(amount: KinAmount, successful: Boolean) = Unit

    override fun remoteSendOutgoing(kin: Kin, currencyCode: com.getcode.model.CurrencyCode) = Unit
    override fun remoteSendIncoming(kin: Kin, currencyCode: com.getcode.model.CurrencyCode, isVoiding: Boolean) = Unit
    override fun recomputed(fxIn: Double, fxOut: Double) = Unit
    override fun grabStart() = Unit
    override fun grab(kin: Kin, currencyCode: com.getcode.model.CurrencyCode) = Unit
    override fun requestShown(amount: KinAmount) = Unit
    override fun requestHidden(amount: KinAmount) = Unit
    override fun cashLinkGrabStart() = Unit
    override fun cashLinkGrab(kin: Kin, currencyCode: com.getcode.model.CurrencyCode) = Unit
    override fun migration(amount: Kin) = Unit
    override fun upgradePrivacy(successful: Boolean, intentId: PublicKey, actionCount: Int) = Unit
    override fun onBillReceived() = Unit
    override fun tipCardShown(username: String) = Unit
    override fun tipCardLinked() = Unit
    override fun backgroundSwapInitiated() = Unit
    override fun unintentionalLogout() = Unit
    override fun appSettingToggled(setting: AppSetting, value: Boolean) = Unit
    override fun photoScanned(successful: Boolean, timeToScanInMillis: Long) = Unit

    override fun action(action: Action, source: ActionSource?) = Unit
}