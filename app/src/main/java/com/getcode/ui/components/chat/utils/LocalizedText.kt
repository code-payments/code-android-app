package com.getcode.ui.components.chat.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.getcode.BuildConfig
import com.getcode.LocalCurrencyUtils
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.model.Currency
import com.getcode.model.Domain
import com.getcode.model.GenericAmount
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Title
import com.getcode.model.chat.Verb
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.util.formatted
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.FormatUtils
import timber.log.Timber
import java.util.Locale

internal fun MessageContent.localizedText(
    title: String,
    resources: ResourceHelper,
    currencyUtils: CurrencyUtils,
): String {
    return when (val content = this) {
        is MessageContent.Exchange -> {
            val amount = when (val kinAmount = content.amount) {
                is GenericAmount.Exact -> {
                    Timber.d("exact")
                    val currency = currencyUtils.getCurrency(kinAmount.currencyCode.name)
                    kinAmount.amount.formatted(
                        resources = resources,
                        currency = currency ?: Currency.Kin
                    )
                }

                is GenericAmount.Partial -> {
                    Timber.d("partial")
                    FormatUtils.formatCurrency(kinAmount.fiat.amount, kinAmount.currencyCode).let {
                        "$it ${resources.getString(R.string.core_ofKin)}"
                    }
                }
            }

            val localized = content.verb.localizedText(resources)

            when (content.verb) {
                Verb.Deposited,
                Verb.Gave,
                Verb.Paid,
                Verb.Purchased,
                Verb.Received,
                Verb.Sent,
                Verb.Spent,
                Verb.Unknown,
                Verb.Withdrew,
                Verb.ReceivedTip,
                Verb.SentTip -> {
                    "$localized $amount"
                }

                Verb.Returned -> {
                    "$amount $localized"
                }
            }
        }

        is MessageContent.Localized -> {
            val resId = resources.getIdentifier(
                content.value.replace(".", "_"),
                ResourceType.String,
            ).let { if (it == 0) null else it }

            resId?.let { resources.getString(it) } ?: content.value
        }

        is MessageContent.SodiumBox -> {
            val organizer = SessionManager.getOrganizer() ?: return "<! encrypted content !>"
            val domain = Domain.from(title) ?: return "<! encrypted content !>"
            val relationship = organizer.relationshipFor(domain) ?: return "<! encrypted content !>"
            val decrypted = content.data.decryptMessageUsingNaClBox(
                keyPair = relationship.getCluster().authority.keyPair
            ) ?: return "<! encrypted content !>"

            decrypted
        }

        is MessageContent.Decrypted -> content.data
        is MessageContent.IdentityRevealed -> {
            val resId = if (content.isFromSelf) R.string.title_chat_announcement_identityRevealed
            else R.string.title_chat_announcement_identityRevealedToYou

            resources.getString(resId, content.identity.username)
        }

        is MessageContent.RawText -> content.value
        is MessageContent.ThankYou -> {
            val resId = if (content.isFromSelf) R.string.title_chat_announcement_thanksSent
            else R.string.title_chat_announcement_thanksReceived

            resources.getString(resId, "some username")
        }
    }
}

internal val MessageContent.localizedText: String
    @Composable get() {
        val context = LocalContext.current
        return when (val content = this) {
            is MessageContent.Exchange -> {
                val amount = when (val kinAmount = content.amount) {
                    is GenericAmount.Exact -> {
                        val currency =
                            LocalCurrencyUtils.current?.getCurrency(kinAmount.currencyCode.name)
                        kinAmount.amount.formatted(currency = currency ?: Currency.Kin)
                    }

                    is GenericAmount.Partial -> {
                        FormatUtils.formatCurrency(kinAmount.fiat.amount, kinAmount.currencyCode)
                            .let {
                                "$it ${context.getString(R.string.core_ofKin)}"
                            }
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
                    Verb.Withdrew,
                    Verb.ReceivedTip,
                    Verb.SentTip -> {
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
                        content.value.replace(".", "_"),
                        "string",
                        BuildConfig.APPLICATION_ID
                    ).let { if (it == 0) null else it }

                    resId?.let { getString(it) } ?: content.value
                }
            }

            is MessageContent.SodiumBox -> "<! encrypted content !>"
            is MessageContent.Decrypted -> content.data
            is MessageContent.IdentityRevealed -> {
                with(LocalContext.current) {
                    val resId = if (content.isFromSelf) R.string.title_chat_announcement_identityRevealed
                    else R.string.title_chat_announcement_identityRevealedToYou

                    getString(resId, content.identity.username)
                }
            }
            is MessageContent.RawText -> content.value
            is MessageContent.ThankYou -> {
                with(LocalContext.current) {
                    val resId = if (content.isFromSelf) R.string.title_chat_announcement_thanksSent
                    else R.string.title_chat_announcement_thanksReceived

                    getString(resId, "some username")
                }
            }
        }
    }

fun Verb.localizedText(resources: ResourceHelper): String {
    val resId = when (this@localizedText) {
        Verb.Deposited,
        Verb.Gave,
        Verb.Paid,
        Verb.Purchased,
        Verb.Received,
        Verb.Sent,
        Verb.Spent,
        Verb.Withdrew -> {
            resources.getIdentifier(
                "subtitle_you${this@localizedText.toString().capitalize(Locale.ENGLISH)}",
                ResourceType.String,
            ).let { if (it == 0) null else it }
        }

        Verb.ReceivedTip -> {
            resources.getIdentifier(
                "subtitle_someoneTippedYou",
                ResourceType.String,
            ).let { if (it == 0) null else it }
        }

        Verb.SentTip -> {
            resources.getIdentifier(
                "subtitle_youTipped",
                ResourceType.String,
            ).let { if (it == 0) null else it }
        }

        Verb.Returned -> {
            resources.getIdentifier(
                "subtitle_was${this@localizedText.toString().capitalize(Locale.ENGLISH)}ToYou",
                ResourceType.String,
            ).let { if (it == 0) null else it }
        }

        Verb.Unknown -> {
            R.string.title_unknown
        }
    }

    return resId?.let { resources.getString(it) } ?: toString()
}

val Verb.localizedText: String
    @SuppressLint("DiscouragedApi")
    @Composable get() = with(LocalContext.current) context@{
        val resId = when (this@localizedText) {
            Verb.Deposited,
            Verb.Gave,
            Verb.Paid,
            Verb.Purchased,
            Verb.Received,
            Verb.Sent,
            Verb.Spent,
            Verb.Withdrew -> {
                resources.getIdentifier(
                    "subtitle_you${this@localizedText.toString().capitalize(Locale.ENGLISH)}",
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }
            }

            Verb.ReceivedTip -> {
                resources.getIdentifier(
                    "subtitle_someoneTippedYou",
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }
            }

            Verb.SentTip -> {
                resources.getIdentifier(
                    "subtitle_youTipped",
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }
            }

            Verb.Returned -> {
                resources.getIdentifier(
                    "subtitle_was${this@localizedText.toString().capitalize(Locale.ENGLISH)}ToYou",
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }
            }

            Verb.Unknown -> {
                R.string.title_unknown
            }
        }

        resId?.let { getString(it) } ?: this@localizedText.toString()
    }

val Title?.localized: String
    @Composable get() = when (val t = this) {
        is Title.Domain -> {
            t.value.capitalize(Locale.getDefault())
        }

        is Title.Localized -> {
            with(LocalContext.current) {
                val resId = resources.getIdentifier(
                    t.value,
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }

                resId?.let { getString(it) } ?: t.value
            }
        }

        else -> ""
    }