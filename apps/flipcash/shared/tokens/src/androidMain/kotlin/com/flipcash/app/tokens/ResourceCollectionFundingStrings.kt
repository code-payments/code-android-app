package com.flipcash.app.tokens

import com.flipcash.shared.tokens.R
import com.getcode.util.resources.ResourceHelper

/** [CollectionFundingStrings] read from the app's string resources. */
class ResourceCollectionFundingStrings(
    private val resources: ResourceHelper,
) : CollectionFundingStrings {
    override val noConversionFees: String
        get() = resources.getString(R.string.label_collectionNoConversionFees)

    override fun plusConversionFees(fee: String): String =
        resources.getString(R.string.label_collectionPlusConversionFees, fee)

    override fun conversionFee(fee: String): String =
        resources.getString(R.string.label_collectionConversionFee, fee)

    override fun feeUnderMinor(minorUnit: String): String =
        resources.getString(R.string.label_collectionFeeUnderMinor, minorUnit)

    override fun feeApprox(fee: String): String =
        resources.getString(R.string.label_collectionFeeApprox, fee)

    override fun shortBy(shortfall: String): String =
        resources.getString(R.string.title_collectionShortBy, shortfall)

    override fun addAndPay(topUp: String, amount: String): String =
        resources.getString(R.string.action_collectionAddAndPay, topUp, amount)

    override val total: String
        get() = resources.getString(R.string.label_paymentDetailsTotal)

    override fun fromBalance(amount: String): String =
        resources.getString(R.string.label_collectionFromBalance, amount)

    override fun fromTopUp(amount: String, source: String): String =
        resources.getString(R.string.label_collectionFromTopUp, amount, source)
}
