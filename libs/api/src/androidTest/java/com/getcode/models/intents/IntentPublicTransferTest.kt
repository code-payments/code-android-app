package com.getcode.models.intents

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.intents.IntentPublicTransfer
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.solana.keys.Key32.Companion.mock
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.SlotType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IntentPublicTransferTest {
    lateinit var context: Context

    private val mnemonic = MnemonicPhrase.newInstance(
        words = "couple divorce usage surprise before range feature source bubble chunk spot away".split(
            " "
        )
    )!!

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun testSendFiveDollars() {
        val organizer = Organizer.newInstance(mnemonic)

        organizer.setBalances(
            mapOf(AccountType.Primary to Kin.fromKin(1_000_000))
        )

        val destination: PublicKey = mock
        val amount = KinAmount.fromFiatAmount(
            fiat = 10.00,
            fx = 0.00001,
            currencyCode = com.getcode.model.CurrencyCode.USD
        )

        val intent = IntentPublicTransfer.newInstance(
            organizer = organizer,
            destination = IntentPublicTransfer.Destination.External(destination),
            amount = amount,
            source = AccountType.Primary
        )

        val resultTray = intent.resultTray

        // Ensure outgoing is NOT incremented
        assertEquals(resultTray.outgoing.getCluster().index, organizer.tray.outgoing.getCluster().index)
        assertEquals(organizer.tray.slotsBalance, intent.resultTray.slotsBalance)

        assertEquals(0.0, resultTray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)

        // Ensure all actions have indexes
        intent.getActions().forEachIndexed { index, action ->
            assertEquals(index, action.id)
        }

        assertEquals(1, intent.getActions().size)

        intent.getAction(0).let { action ->
            action as ActionTransfer
            assertEquals(ActionTransfer.Kind.NoPrivacyTransfer, action.kind)
            assertEquals(destination, action.destination)
        }
    }
}