package com.flipcash.app.analytics

import com.flipcash.analytics.Amount
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversionsTest {

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    @Test
    fun `a Fiat keeps quarks of ten million or more as a whole number`() {
        val fiat = Fiat(quarks = 25_000_000L, currencyCode = CurrencyCode.USD)

        val amount = fiat.analytics

        assertEquals(
            Amount(fiat = fiat.decimalValue, currency = "USD", usdc = fiat.decimalValue, quarks = 25_000_000L),
            amount,
        )
    }

    @Test
    fun `a LocalFiat carries the native amount, the token amount, the rate and the mint`() {
        val token = Fiat(quarks = 25_000_000L, currencyCode = CurrencyCode.USD)
        val native = Fiat(quarks = 34_000_000L, currencyCode = CurrencyCode.CAD)
        val localFiat = LocalFiat(
            underlyingTokenAmount = token,
            nativeAmount = native,
            rate = Rate(fx = 1.36, currency = CurrencyCode.CAD),
            mint = mint,
        )

        val amount = localFiat.analytics

        assertEquals(
            Amount(
                fiat = native.decimalValue,
                currency = "CAD",
                usdc = token.decimalValue,
                quarks = 25_000_000L,
                exchangeRate = 1.36,
                mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            ),
            amount,
        )
    }

    @Test
    fun `every deeplink type maps to its source name`() {
        val id: ID = ByteArray(16).toList()
        val chatId = ChatId(ByteArray(16))
        val types = listOf(
            DeeplinkType.Login(entropy = "e"),
            DeeplinkType.CashLink(entropy = "e"),
            DeeplinkType.TokenInfo(mint = mint),
            DeeplinkType.TipChat(identifier = ChatIdentifier.ByChatId(chatId)),
            DeeplinkType.GroupChatInvite(chatId = chatId),
            DeeplinkType.Tipcard(userId = id),
            DeeplinkType.TipcardByUsername(username = "someone"),
            DeeplinkType.EmailVerification(email = "a@b.c", code = "123"),
        )

        assertEquals(
            listOf(
                "Login", "CashLink", "TokenInfo", "TipChat",
                "GroupChatInvite", "Tipcard", "TipcardByUsername", "EmailVerification",
            ),
            types.map { it.analytics },
        )
    }

    @Test
    fun `withoutQueryOrFragment drops the query and the fragment`() {
        assertEquals(
            "https://app.flipcash.com/c/abc",
            "https://app.flipcash.com/c/abc?a=b#c".withoutQueryOrFragment(),
        )
    }
}
