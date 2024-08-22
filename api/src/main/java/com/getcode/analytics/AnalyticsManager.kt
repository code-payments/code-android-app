package com.getcode.analytics

import com.getcode.api.BuildConfig
import com.getcode.model.AppSetting
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.PrefsBool
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.metrics.Trace
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class Action(val value: String) {
    CreateAccount("Action: Create Account"),
    EnterPhone("Action: Enter Phone"),
    VerifyPhone("Action: Verify Phone"),
    ConfirmAccessKey("Action: Confirm Access Key"),
    CompletedOnboarding("Action: Completed Onboarding"),
}

enum class ActionSource(val value: String) {
    AccessKeySaved("Saved to Photos"),
    AccessKeyWroteDown("Wrote it Down")
}

@Singleton
class AnalyticsManager @Inject constructor(
    private val mixpanelAPI: MixpanelAPI
) : AnalyticsService {
    private var grabStartMillis: Long = 0L
    private var cashLinkGrabStartMillis: Long = 0L

    private var traceAppInit: Trace? = null
    private var timeAppInit: Long? = null

    override fun onAppStart() {
        timeAppInit = System.currentTimeMillis()
        traceAppInit = Firebase.performance.newTrace("Init")
        traceAppInit?.start()
    }

    override fun onAppStarted() {
        traceAppInit ?: return
        traceAppInit?.stop()
        traceAppInit = null
        Timber.i("App init time: " + (System.currentTimeMillis() - (timeAppInit ?: 0)))
    }

    override fun logout() {
        track(Name.Logout)
    }

    override fun login(ownerPublicKey: String, autoCompleteCount: Int, inputChangeCount: Int) {
        track(
            Name.Login,
            Pair(Property.OwnerPublicKey, ownerPublicKey),
            Pair(Property.AutoCompleteCount, autoCompleteCount.toString()),
            Pair(Property.InputChangeCount, inputChangeCount.toString()),
        )
    }

    override fun createAccount(isSuccessful: Boolean, ownerPublicKey: String?) {
        track(
            Name.CreateAccount,
            Pair(Property.Result, isSuccessful.toString()),
            Pair(Property.OwnerPublicKey, ownerPublicKey.orEmpty())
        )
    }

    override fun unintentionalLogout() {
        track(Name.UnintentionalLogout)
    }


    override fun billTimeoutReached(
        kin: Kin,
        currencyCode: CurrencyCode,
        animation: BillPresentationStyle
    ) {
        track(
            Name.Bill,
            Pair(Property.State, StringValue.TimedOut.value),
            Pair(Property.Amount, kin.toKin().toInt().toString()),
            Pair(Property.Currency, currencyCode.name),
            Pair(Property.Animation, animation.value)
        )
    }

    override fun billShown(kin: Kin, currencyCode: CurrencyCode, animation: BillPresentationStyle) {
        track(
            Name.Bill,
            Pair(Property.State, StringValue.Shown.value),
            Pair(Property.Amount, kin.toKin().toInt().toString()),
            Pair(Property.Currency, currencyCode.name),
            Pair(Property.Animation, animation.value)
        )
    }

    override fun billHidden(
        kin: Kin,
        currencyCode: CurrencyCode,
        animation: BillPresentationStyle
    ) {
        track(
            Name.Bill,
            Pair(Property.State, StringValue.Hidden.value),
            Pair(Property.Amount, kin.toKin().toInt().toString()),
            Pair(Property.Currency, currencyCode.name),
            Pair(Property.Animation, animation.value)
        )
    }

    override fun transfer(amount: KinAmount, successful: Boolean) {
        track(
            Name.Transfer,
            Property.State to if (successful) StringValue.Success.value else StringValue.Failure.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Fx to amount.rate.fx.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun transferForRequest(amount: KinAmount, successful: Boolean) {
        track(
            Name.RequestPayment,
            Property.State to if (successful) StringValue.Success.value else StringValue.Failure.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Fx to amount.rate.fx.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun transferForTip(amount: KinAmount, successful: Boolean) {
        track(
            Name.Tip,
            Property.State to if (successful) StringValue.Success.value else StringValue.Failure.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Fx to amount.rate.fx.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun remoteSendOutgoing(kin: Kin, currencyCode: CurrencyCode) {
        track(
            Name.RemoteSendOutgoing,
            Property.Amount to kin.toKin().toInt().toString(),
            Property.Currency to currencyCode.name
        )
    }

    override fun remoteSendIncoming(kin: Kin, currencyCode: CurrencyCode, isVoiding: Boolean) {
        track(
            Name.RemoteSendIncoming,
            Property.VoidingSend to if (isVoiding) StringValue.Yes.value else StringValue.No.value,
            Property.Amount to kin.toKin().toInt().toString(),
            Property.Currency to currencyCode.name
        )
    }

    override fun recomputed(fxIn: Double, fxOut: Double) {
        val delta = ((fxOut / fxIn) - 1) * 100
        track(
            Name.Recompute,
            Property.PercentDelta to delta.toString()
        )
    }

    override fun grabStart() {
        grabStartMillis = System.currentTimeMillis()
    }

    override fun grab(kin: Kin, currencyCode: CurrencyCode) {
        if (grabStartMillis == 0L) return
        val millisecondsToGrab = System.currentTimeMillis() - grabStartMillis
        track(
            Name.Grab,
            Pair(Property.Amount, kin.toKin().toInt().toString()),
            Pair(Property.Currency, currencyCode.name),
            Pair(Property.GrabTime, String.format("%,.2f", millisecondsToGrab / 1000.0))
        )
        grabStartMillis = 0
    }

    override fun requestShown(amount: KinAmount) {
        track(
            Name.Request,
            Property.State to StringValue.Shown.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun requestHidden(amount: KinAmount) {
        track(
            Name.Request,
            Property.State to StringValue.Hidden.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun cashLinkGrabStart() {
        cashLinkGrabStartMillis = System.currentTimeMillis()
    }

    override fun cashLinkGrab(kin: Kin, currencyCode: CurrencyCode) {
        if (cashLinkGrabStartMillis == 0L) return
        val millisecondsToGrab = System.currentTimeMillis() - cashLinkGrabStartMillis
        track(
            Name.CashLinkGrab,
            Pair(Property.Amount, kin.toKin().toInt().toString()),
            Pair(Property.Currency, currencyCode.name),
            Pair(Property.GrabTime, String.format("%,.2f", millisecondsToGrab / 1000.0))
        )
        cashLinkGrabStartMillis = 0
    }

    override fun migration(amount: Kin) {
        track(
            Name.PrivacyMigration,
            Pair(Property.Amount, amount.toKin().toInt().toString())
        )
    }

    override fun upgradePrivacy(successful: Boolean, intentId: PublicKey, actionCount: Int) {
        track(
            Name.UpgradePrivacy,
            Pair(
                Property.State,
                if (successful) StringValue.Success.value else StringValue.Failure.value
            ),
            Pair(Property.IntentId, intentId.base58()),
            Pair(Property.ActionCount, actionCount.toString())
        )
    }

    override fun withdrawal(amount: KinAmount, successful: Boolean) {
        track(
            Name.Withdrawal,
            Property.State to if (successful) StringValue.Success.value else StringValue.Failure.value,
            Property.Amount to amount.kin.toKin().toInt().toString(),
            Property.Fiat to amount.fiat.toString(),
            Property.Fx to amount.rate.fx.toString(),
            Property.Currency to amount.rate.currency.name,
        )
    }

    override fun onBillReceived() {
        Timber.i("Bill scanned. From start: " + (System.currentTimeMillis() - (timeAppInit ?: 0)))
    }

    override fun tipCardShown(username: String) {
        track(
            Name.TipCard,
            Property.State to StringValue.Shown.value,
            Property.xUsername to username,
        )
    }

    override fun tipCardLinked() {
        track(Name.TipCardLinked)
    }

    override fun backgroundSwapInitiated() {
        track(Name.BackgroundSwap)
    }

    override fun appSettingToggled(setting: AppSetting, value: Boolean) {
        val name = when (setting) {
            PrefsBool.CAMERA_START_BY_DEFAULT -> Name.AutoStartCamera
            PrefsBool.REQUIRE_BIOMETRICS -> Name.RequireBiometrics
        }

        track(
            name,
            Property.State to if (value) StringValue.Yes.value else StringValue.No.value,
        )
    }

    override fun action(action: Action, source: ActionSource?) {
        track(
            action = action,
            properties = source?.let { arrayOf(Property.Source to it.value) }.orEmpty()
        )
    }

    private fun track(event: Name, vararg properties: Pair<Property, String>) {
        track(name = event.value, properties = properties)
    }

    private fun track(action: Action, vararg properties: Pair<Property, String>) {
       track(name = action.value, properties = properties)
    }

    private fun track(name: String, vararg properties: Pair<Property, String>) {
        if (BuildConfig.DEBUG) {
            Timber.d("debug track $name, ${properties.map { "${it.first.name}, ${it.second}" }}")
            return
        } //no logging in debug

        val jsonObject = JSONObject()
        properties.forEach { property ->
            jsonObject.put(property.first.value, property.second)
        }
        mixpanelAPI.track(name, jsonObject)
    }

    enum class Name(val value: String) {
        //Account
        Logout("Logout"),
        Login("Login"),
        CreateAccount("Create Account"),
        UnintentionalLogout("Unintentional Logout"),
        TipCardLinked("Tip Card Linked"),

        //Bill
        Bill("Bill"),
        Request("Request Card"),
        TipCard("Tip Card"),

        //Transfer
        Transfer("Transfer"),
        RequestPayment("Request Payment"),
        Tip("Tip"),
        RemoteSendOutgoing("Remote Send Outgoing"),
        RemoteSendIncoming("Remote Send Incoming"),
        Grab("Grab"),
        CashLinkGrab("Cash Link Grab"),
        UpgradePrivacy("Upgrade Privacy"),
        ClaimGetFreeKin("Claim Get Free Kin"),
        PrivacyMigration("Privacy Migration"),
        BackgroundSwap("Background Swap Initiated"),
        Withdrawal("Withdrawal"),

        // Errors
        ErrorRequest("Error Request"),

        Recompute("Recompute"),

        // App Settings
        AutoStartCamera("Camera Auto Start"),
        RequireBiometrics("Require Biometrics")
    }

    enum class Property(val value: String) {
        //Open
        Screen("Screen"),

        // Account
        OwnerPublicKey("Owner Public Key"),
        AutoCompleteCount("Auto-complete count"),
        InputChangeCount("Input change count"),
        Result("Result"),
        MillisecondsToConfirm("Milliseconds to confirm"),
        GrabTime("Grab Time"),

        // Bill
        State("State"),
        Amount("Amount"),
        Fiat("Fiat"),
        Fx("Exchange Rate"),
        Currency("Currency"),
        Animation("Animation"),
        Rendezvous("Rendezvous"),
        xUsername("X Username"),

        // Validation
        Type("Type"),
        Error("Error"),

        // Privacy Upgrade
        IntentId("Intent ID"),
        ActionCount("Action Count"),

        // Remote Send
        VoidingSend("Voiding Send"),

        PercentDelta("Percent Delta"),

        Source("Source"),
    }

    enum class BillPresentationStyle(val value: String) {
        Pop("Pop"),
        Slide("Slide"),
    }

    enum class StringValue(val value: String) {
        Success("Success"),
        Failure("Failure"),
        Yes("Yes"),
        No("No"),
        Shown("Shown"),
        Hidden("Hidden"),
        TimedOut("Timed Out"),
    }
}