package com.getcode.models.intents

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin
import com.getcode.model.intents.IntentReceive
import com.getcode.model.intents.actions.ActionCloseEmptyAccount
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.SlotType
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class IntentReceiveTest {
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
        val organizer = Organizer.newInstance(context, mnemonic)
        val amount = Kin.Companion.fromKin(1_000_000)

        organizer.setBalances(
            mapOf(AccountType.Incoming to amount)
        )

        val previousIncoming = organizer.tray.incoming

        val intent = IntentReceive.newInstance(
            context = context,
            organizer = organizer,
            amount = amount
        )

        Assert.assertNotEquals(intent.id, PublicKey(ByteArray(LENGTH_32).toList()))

        val resultTray = intent.resultTray

        // Ensure incoming is incremented
        Assert.assertEquals(organizer.tray.incoming.getCluster().index, resultTray.incoming.getCluster().index - 1)

        // The incoming account has been rotated so we need to ensure
        // the previous incoming account has the correct balance and
        // the new account is empty
        Assert.assertEquals(0.0, resultTray.incoming.partialBalance.toKinValueDouble(), 0.0)

        Assert.assertEquals(1_000_000.0, resultTray.availableBalance.toKinValueDouble(), 0.0)

        Assert.assertEquals(0.0, resultTray.owner.partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(0.0, resultTray.incoming.partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(0.0, resultTray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        Assert.assertEquals(10.0, resultTray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(90.0, resultTray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(900.0, resultTray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(9_000.0, resultTray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(90_000.0, resultTray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(900_000.0, resultTray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        Assert.assertEquals(0.0, resultTray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)

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
         *  Close Empty   -> G2JXRCvg2PVXHd9veJ5MCcR38723tcuz52Mtw7uE4QK8 (closeEmptyAccount)
         *  Open          -> 7YHmKkV675HNVMpUeFgWssyQZxxFoFG9gRu6RtR2KEfg (openAccount)
         *  Close Dormant -> 7YHmKkV675HNVMpUeFgWssyQZxxFoFG9gRu6RtR2KEfg sending to 8DDrALtni72M6FnCiTMToEssMHDEH3KRP1nhA6svQDxp (closeDormantAccount)
         */

        intent.getActions().forEachIndexed { index, action ->
            Assert.assertEquals(index, action.id)
        }

        Assert.assertEquals(10, intent.getActions().size)

        (intent.getAction(0) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyTransfer, action.kind)
            Assert.assertEquals(1_000_000.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.incoming.getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[6].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(1) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(1_000_000.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[6].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[5].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(2) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(100_000.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[5].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[4].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(3) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(10_000.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[4].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[3].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(4) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(1_000.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[3].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[2].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(5) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(100.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[2].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[1].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(6) as ActionTransfer).let { action ->
            Assert.assertEquals(ActionTransfer.Kind.TempPrivacyExchange, action.kind)
            Assert.assertEquals(10.0, action.amount.toKinValueDouble(), 0.0)
            Assert.assertEquals(organizer.tray.slots[1].getCluster(), action.source)
            Assert.assertEquals(
                organizer.tray.slots[0].getCluster().timelockAccounts.vault.publicKey,
                action.destination
            )
        }

        (intent.getAction(7) as ActionCloseEmptyAccount).let { action ->
            Assert.assertEquals(AccountType.Incoming, action.type)
            Assert.assertEquals(previousIncoming.getCluster(), action.cluster)
        }

        (intent.getAction(8) as ActionOpenAccount).let { action ->
            Assert.assertEquals(AccountType.Incoming, action.type)
            Assert.assertEquals(resultTray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(), action.owner)
            Assert.assertEquals(resultTray.incoming.getCluster(), action.accountCluster)
        }

        (intent.getAction(9) as ActionWithdraw).let { action ->
            Assert.assertEquals(ActionWithdraw.Kind.CloseDormantAccount(AccountType.Incoming), action.kind)
            Assert.assertEquals(resultTray.incoming.getCluster(), action.cluster)
            Assert.assertEquals(resultTray.owner.getCluster().timelockAccounts.vault.publicKey, action.destination)
        }
    }
}