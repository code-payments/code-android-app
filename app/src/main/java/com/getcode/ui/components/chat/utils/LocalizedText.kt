package com.getcode.ui.components.chat.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.getcode.BuildConfig
import com.getcode.LocalCurrencyUtils
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.GenericAmount
import com.getcode.model.MessageContent
import com.getcode.model.Verb
import com.getcode.util.Kin
import com.getcode.util.formatted
import com.getcode.utils.FormatUtils
import java.util.Locale

internal val MessageContent.localizedText: String
    @Composable get() {
        return when (val content = this) {
            is MessageContent.Exchange -> {
                val amount = when (val kinAmount = content.amount) {
                    is GenericAmount.Exact -> {
                        val currency =
                            LocalCurrencyUtils.current?.getCurrency(kinAmount.currencyCode.name)
                        kinAmount.amount.formatted(currency = currency ?: Currency.Kin)
                    }

                    is GenericAmount.Partial -> {
                        FormatUtils.formatCurrency(kinAmount.fiat.amount, Locale.getDefault())
                    }
                }

                val localized = content.verb.localizedText

                when (content.verb) {
                    Verb.Deposited,
                    Verb.Gave,
                    Verb.Paid,
                    Verb.Purchased,
                    Verb.Received,
                    Verb.Sent,
                    Verb.Spent,
                    Verb.Unknown,
                    Verb.Withdrew -> {
                        "$localized $amount"
                    }
                    Verb.Returned -> {
                        "$amount $localized"
                    }

                }
            }

            is MessageContent.Localized -> {
                with(LocalContext.current) {
                    val resId = resources.getIdentifier(
                        content.value,
                        "string",
                        BuildConfig.APPLICATION_ID
                    ).let { if (it == 0) null else it }

                    resId?.let { getString(it) }.orEmpty()
                }
            }

            MessageContent.SodiumBox -> "<! encrypted content !>"
        }
    }

val Verb.localizedText: String
    @SuppressLint("DiscouragedApi")
    @Composable get() = with(LocalContext.current) context@{
        if (this@localizedText == Verb.Unknown) stringResource(id = R.string.title_unknown)
        val resId = resources.getIdentifier(
            "subtitle_verb_${this@localizedText.toString().lowercase()}",
            "string",
            BuildConfig.APPLICATION_ID
        ).let { if (it == 0) null else it }

        resId?.let { getString(it) }.orEmpty()
    }