package com.getcode.models.intents

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.intents.IntentPrivateTransfer
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.Key32.Companion.mock
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.SlotType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IntentPrivateTransferTest {
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
        val organizer = Organizer.newInstance(context, mnemonic)

        organizer.setBalances(
            mapOf(AccountType.Bucket(SlotType.Bucket1m) to Kin.fromKin(1_000_000))
        )

        val destination: PublicKey = mock
        val amount = KinAmount.fromFiatAmount(
            fiat = 5.00,
            fx = 0.00001,
            currencyCode = CurrencyCode.USD
        )

        val rendezvous = PublicKey.generate()

        val intent = IntentPrivateTransfer.newInstance(
            context = context,
            rendezvousKey = rendezvous,
            organizer = organizer,
            destination = destination,
            amount = amount,
            isWithdrawal = false
        )

        assertEquals(rendezvous, intent.id)

        val resultTray = intent.resultTray

        // Ensure outgoing is incremented
        assertEquals(organizer.tray.outgoing.getCluster().index, resultTray.outgoing.getCluster().index - 1)

        // The outgoing account has been rotated so we need to ensure
        // the previous outgoing account has the correct balance and
        // the new account is empty
        assertEquals(0.0, resultTray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        assertEquals(500_000.0, organizer.tray.availableBalance.toKinValueDouble() - intent.resultTray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(10.0, resultTray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90.0, resultTray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900.0, resultTray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, resultTray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90_000.0, resultTray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(400_000.0, resultTray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, resultTray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)

        /*
         *  Expected actions:
         *
         *  K 1000000 (0) -> BEZasPLNZ5vsHH3SfdxeWuTD5uXm8pPUmbyrZkPJqQwr (tempPrivacyExchange)
         *  K 100000 (0)  -> 7GpxPmL2sGqRq1ru4nKTPWPRruemat5BCReGmNLNXsRE (tempPrivacyExchange)
         *  K 500000 (0)  -> Gfuc6w9vPwoGKtRwEv7YJtxGWtR4knLGoMoT5Hu1eS6A (tempPrivacyTransfer)
         *  K 500000 (0)  -> EBDRoayCDDUvDgCimta45ajQeXbexv7aKqJubruqpyvu (noPrivacyWithdraw)
         *  K 10000 (0)   -> 6upXkqkiY3GYBqm3wSReuAsaWxQSY1d67GuRxLhM74Va (tempPrivacyExchange)
         *  K 1000 (0)    -> 6eqAKwBqtAQ28juRdc3429GoRpUTuu86gScJrDN6cqGQ (tempPrivacyExchange)
         *  K 100 (0)     -> BbCteP1N7DiyShnEuCRGkNLeoYbr7v1d5deGDBbeu5Zg (tempPrivacyExchange)
         *  K 10 (0)      -> J5fzggJmRyPwmAcJw7iVD9jG4q4xZyeELNEEjfhKxp4i (tempPrivacyExchange)
         *  Close Dormant -> Gfuc6w9vPwoGKtRwEv7YJtxGWtR4knLGoMoT5Hu1eS6A sending to 8DDrALtni72M6FnCiTMToEssMHDEH3KRP1nhA6svQDxp (closeDormantAccount)
         *  Open          -> 48FpNcnDn5kdFPRjsm5dzr7iXSVBPuF2GcKYbyyrQFD6 (openAccount)
         */

        // Ensure all actions have indexes
        intent.getActions().forEachIndexed { index, action ->
            assertEquals(index, action.id)
        }

        assertEquals(10, intent.getActions().size)

        (intent.getAction(0) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(1_000_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[5].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(1) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(100_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[4].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(2) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyTransfer, action.kind)
            assertEquals(500_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.outgoing.getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(3) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(10_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[3].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(4) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(1_000.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[2].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(5) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(100.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[1].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(6) as ActionTransfer).let { action ->
            assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            assertEquals(10.0, action.amount.toKinValueDouble(), 0.0)
            assertEquals(organizer.tray.slots[0].getCluster().vaultPublicKey, action.destination)
        }

        (intent.getAction(7) as ActionWithdraw).let { action ->
            assertEquals(ActionWithdraw.Kind.NoPrivacyWithdraw(Kin.fromKin(500_000)), action.kind)
            assertEquals(destination, action.destination)
        }

        (intent.getAction(8) as ActionOpenAccount).let { action ->
            assertEquals(AccountType.Outgoing, action.type)
            assertEquals(resultTray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(), action.owner)
            assertEquals(resultTray.outgoing.getCluster(), action.accountCluster)
        }

        (intent.getAction(9) as ActionWithdraw).let { action ->
            assertEquals(ActionWithdraw.Kind.CloseDormantAccount(AccountType.Outgoing), action.kind)
            assertEquals(resultTray.outgoing.getCluster(), action.cluster)
            assertEquals(organizer.tray.owner.getCluster().vaultPublicKey, action.destination)
        }

    }
}