package com.getcode.solana.organizer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivePath.Companion.primary
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicCache
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.AccountInfo
import com.getcode.model.Kin
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrganizerTest {
    lateinit var context: Context

    private val mnemonic = MnemonicPhrase.newInstance(
        words = "couple divorce usage surprise before range feature source bubble chunk spot away".split(
            " "
        )
    )!!

    lateinit var owner: DerivedKey

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        MnemonicCache.init(context)
        owner = DerivedKey(
            path = primary,
            keyPair = mnemonic.getSolanaKeyPair()
        )
    }

    @Test
    fun testInit() {
        fun derive(path: DerivePath): DerivedKey {
            return DerivedKey.derive(
                path,
                mnemonic
            )
        }

        val organizer = Organizer.newInstance(
            mnemonic = mnemonic
        )

        assertEquals(organizer.tray.owner.getCluster(), AccountCluster.newInstance(authority = owner, kind = AccountCluster.Kind.Timelock))

        assertEquals(7, organizer.tray.slots.size)

        assertEquals(
            organizer.tray.incoming.getCluster(),
            AccountCluster.newInstance(
                authority = derive(DerivePath.getBucketIncoming(0)),
                kind = AccountCluster.Kind.Timelock
            )
        )

        assertEquals(
            organizer.tray.outgoing.getCluster(),
            AccountCluster.newInstance(
                authority = derive(DerivePath.getBucketOutgoing(0)),
                kind = AccountCluster.Kind.Timelock
            )
        )

        assertEquals(
            listOf(
                AccountCluster.newInstance(authority = derive(Denomination.ones.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.tens.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.hundreds.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.thousands.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.tenThousands.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.hundredThousands.derivationPath), kind = AccountCluster.Kind.Timelock),
                AccountCluster.newInstance(authority = derive(Denomination.millions.derivationPath), kind = AccountCluster.Kind.Timelock)
            ),
            organizer.tray.slots.map { it.getCluster() }
        )
    }

    @Test
    fun testAllAccounts() {
        val organizer = Organizer.newInstance(
            mnemonic = mnemonic
        )

        val accounts = organizer.allAccounts()

        assertEquals(accounts.size, 10)

        assertEquals(1, accounts.filter { it.first == AccountType.Primary }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Incoming }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Outgoing }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket1) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket10) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket100) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket1k) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket10k) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket100k) }.size)
        assertEquals(1, accounts.filter { it.first == AccountType.Bucket(SlotType.Bucket1m) }.size)
    }

    @Test
    fun testAccountCluster() {
        val cluster = AccountCluster.newInstance(authority = owner, kind = AccountCluster.Kind.Timelock)
        val timelockAccounts =
            TimelockDerivedAccounts.newInstance(owner = owner.keyPair.publicKeyBytes.toPublicKey())

        assertEquals(cluster.authority, owner)
        assertEquals(cluster.timelock, timelockAccounts)
    }

    @Test
    fun testUnlockedState() {
        val organizer = Organizer.newInstance(
            mnemonic = mnemonic
        )

        assertFalse(organizer.isUnuseable)

        AccountInfo.ManagementState.entries.forEach { state ->
                organizer.setAccountInfo(
                    mapOf(
                        organizer.primaryVault
                            to
                        AccountInfo(
                            index = 0,
                            accountType = AccountType.Primary,
                            address = organizer.primaryVault,
                            owner = null,
                            authority = null,
                            balanceSource = AccountInfo.BalanceSource.Blockchain,
                            balance = Kin.Companion.fromKin(15),
                            managementState = state,
                            blockchainState = AccountInfo.BlockchainState.Exists,
                            claimState = AccountInfo.ClaimState.Unknown,
                            mustRotate = false,
                            originalKinAmount = null,
                            relationship = null,
                            createdAt = System.currentTimeMillis(),
                        )
                )
            )

            if (state == AccountInfo.ManagementState.Locked || state == AccountInfo.ManagementState.None) {
                assertFalse(organizer.isUnuseable)
            } else {
                assertTrue(organizer.isUnuseable)
            }
        }
    }
}
