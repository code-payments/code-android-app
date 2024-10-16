package com.getcode.models.intents

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin
import com.getcode.model.intents.IntentDeposit
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class IntentDepositTest {
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
    fun testReceiveSevenDollars() {
        val organizer = Organizer.newInstance(mnemonic)
        val amount = Kin.Companion.fromKin(1_000_000)

        organizer.setBalances(
            mapOf(AccountType.Primary to amount)
        )

        val intent = IntentDeposit.newInstance(
            source = AccountType.Primary,
            organizer = organizer,
            amount = amount
        )


        assertNotEquals(intent.id,
            com.getcode.solana.keys.PublicKey(ByteArray(com.getcode.solana.keys.LENGTH_32).toList())
        )

        val resultTray = intent.resultTray

        // Ensure the funds have been moved out of the
        // primary accounts and into the tray slots
        assertEquals(0.0, resultTray.owner.partialBalance.toKinValueDouble(), 0.0)

        assertEquals(1_000_000.0, resultTray.availableBalance.toKinValueDouble(), 0.0)

        assertEquals(0.0, resultTray.owner.partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.incoming.partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        assertEquals(10.0, resultTray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90.0, resultTray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900.0, resultTray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, resultTray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90_000.0, resultTray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900_000.0, resultTray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)

        /*
         *  Expected actions:
         *
         *  K 1000000 (0) -> AB4w6m9nhQaagqpnu6TcsgE1Z34wXKwKSoxU6tGadAfN (tempPrivacyTransfer)
         *  K 1000000 (0) -> BEZasPLNZ5vsHH3SfdxeWuTD5uXm8pPUmbyrZkPJqQwr (tempPrivacyExchange)
         *  K 100000 (0)  -> 7GpxPmL2sGqRq1ru4nKTPWPRruemat5BCReGmNLNXsRE (tempPrivacyExchange)
         *  K 10000 (0)   -> 6upXkqkiY3GYBqm3wSReuAsaWxQSY1d67GuRxLhM74Va (tempPrivacyExchange)
         *  K 1000 (0)    -> 6eqAKwBqtAQ28juRdc3429GoRpUTuu86gScJrDN6cqGQ (tempPrivacyExchange)
         *  K 100 (0)     -> BbCteP1N7DiyShnEuCRGkNLeoYbr7v1d5deGDBbeu5Zg (tempPrivacyExchange)
         *  K 10 (0)      -> J5fzggJmRyPwmAcJw7iVD9jG4q4xZyeELNEEjfhKxp4i (tempPrivacyExchange)
         */

        intent.getActions().forEachIndexed { index, action ->
            assertEquals(index, action.id)
        }

        assertEquals(7, intent.getActions().size)

        (intent.getAction(0) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyTransfer, action.kind)
            assertEquals(1_000_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.owner.getCluster(), action.source)
            assertEquals(organizer.tray.slots[6].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(1) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(1_000_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[6].getCluster(), action.source)
            assertEquals(organizer.tray.slots[5].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(2) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(100_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[5].getCluster(), action.source)
            assertEquals(organizer.tray.slots[4].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(3) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(10_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[4].getCluster(), action.source)
            assertEquals(organizer.tray.slots[3].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(4) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(1_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[3].getCluster(), action.source)
            assertEquals(organizer.tray.slots[2].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(5) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(100.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[2].getCluster(), action.source)
            assertEquals(organizer.tray.slots[1].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(6) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(10.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[1].getCluster(), action.source)
            assertEquals(organizer.tray.slots[0].getCluster().vaultPublicKey, action.destination)
        }

    }
}