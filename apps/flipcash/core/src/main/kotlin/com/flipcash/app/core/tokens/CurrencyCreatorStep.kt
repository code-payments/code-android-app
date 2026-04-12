package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import com.getcode.opencode.model.ui.TokenBillCustomizations
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface CurrencyCreatorStep: FlowStep, Parcelable {

    @Parcelize
    @Serializable
    data object Info : CurrencyCreatorStep

    @Parcelize
    @Serializable
    data class NameSelection(val name: String? = null) : CurrencyCreatorStep

    @Parcelize
    @Serializable
    data class IconSelection(val icon: ByteArray? = null): CurrencyCreatorStep {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (other !is IconSelection) return false
            return icon.contentEquals(other.icon)
        }

        override fun hashCode(): Int {
            return icon?.contentHashCode() ?: 0
        }
    }

    @Parcelize
    @Serializable
    data class DescriptionSelection(val description: String? = null): CurrencyCreatorStep

    @Parcelize
    @Serializable
    data class BillCustomization(val customizations: TokenBillCustomizations? = null): CurrencyCreatorStep

    @Parcelize
    @Serializable
    data object BillReviewAndPurchase: CurrencyCreatorStep

    @Parcelize
    @Serializable
    data object Processing: CurrencyCreatorStep
}