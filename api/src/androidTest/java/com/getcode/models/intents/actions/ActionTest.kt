package com.getcode.models.intents.actions

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.keys.Hash
import com.getcode.model.Kin
import com.getcode.model.intents.ServerParameter
import com.getcode.model.intents.actions.*
import com.getcode.network.repository.decodeBase58
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.Key32.Companion.mock
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.SlotType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionTest {
    lateinit var context: Context
    lateinit var organizer: Organizer

    private val mnemonic = MnemonicPhrase.newInstance(
        words = "couple divorce usage surprise before range feature source bubble chunk spot away".split(
            " "
        )
    )!!

    private val nonce      = PublicKey.fromBase58(base58 = "JDwJWHij1E75GVAAcMUPkwDgC598wRdF4a7d76QX895S")
    private val blockhash  = PublicKey.fromBase58(base58 = "BXLEqnSJxMHvEJQHRMSbsFQGDpBn891BpQo828xejbi1")
    private val treasury   = PublicKey.fromBase58(base58 = "Ddk7k7zMMWsp8fZB12wqbiADdXKQFWfwUUsxSo73JaQ9")
    private val recentRoot = PublicKey.fromBase58(base58 = "2sDAFcEZkLd3mbm6SaZhifctkyB4NWsp94GMnfDs1BfR")

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        organizer = Organizer.newInstance(mnemonic)
    }

    @Test
    fun testSignatureProvidedByCloseDormantAccount() {
        val action = ActionWithdraw.newInstance(
            kind = ActionWithdraw.Kind.CloseDormantAccount(AccountType.Incoming),
            cluster = organizer.tray.outgoing.getCluster(),
            destination = mock
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = basicConfig

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByNoPrivacyWithdraw() {
        val action = ActionWithdraw.newInstance(
            kind = ActionWithdraw.Kind.NoPrivacyWithdraw(Kin.Companion.fromKin(10)),
            cluster = organizer.tray.outgoing.getCluster(),
            destination = mock
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = basicConfig

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByTempPrivacyTransfer() {
        val action = ActionTransfer.newInstance(
            kind = ActionTransfer.Kind.TempPrivacyTransfer,
            intentId = mock,
            amount = Kin.fromKin(1),
            source = organizer.tray.cluster(AccountType.Bucket(SlotType.Bucket1)),
            destination = mock
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = ServerParameter(
            actionId = 0,
            parameter = ServerParameter.Parameter.TempPrivacy(
                treasury = treasury,
                recentRoot = recentRoot
            ),
            configs = listOf(
                ServerParameter.Config(
                    nonce = nonce,
                    blockhash = blockhash
                )
            )
        )

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByTempPrivacyExchange() {
        val action = ActionTransfer.newInstance(
            kind = ActionTransfer.Kind.TempPrivacyExchange,
            intentId = mock,
            amount = Kin.fromKin(1),
            source = organizer.tray.cluster(AccountType.Bucket(SlotType.Bucket1)),
            destination = mock
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = tempPrivacy

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByNoPrivacyTransfer() {
        val action = ActionTransfer.newInstance(
            kind = ActionTransfer.Kind.NoPrivacyTransfer,
            intentId = mock,
            amount = Kin.fromKin(1),
            source = organizer.tray.cluster(AccountType.Bucket(SlotType.Bucket1)),
            destination = mock
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = tempPrivacy

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByClosedEmptyAccount() {
        val action = ActionCloseEmptyAccount.newInstance(
            type = AccountType.Incoming,
            cluster = organizer.tray.incoming.getCluster()
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = basicConfig

        assertEquals(1, action.signatures().size)
    }

    @Test
    fun testSignatureProvidedByPrivacyUpgrade() {
        val action = ActionPrivacyUpgrade.newInstance(
            source = organizer.tray.incoming.getCluster(),
            originalActionID = 0,
            originalCommitmentStateAccount = leaf,
            originalAmount = Kin.fromKin(1),
            originalNonce = nonce,
            originalRecentBlockhash = blockhash,
            treasury = treasury
        )

        assertThrows(ActionPrivacyUpgradeException.MissingServerParameterException::class.java) {
            action.signatures().isEmpty()
        }

        action.serverParameter = privacyUpgrade
        // Only validates that signatures are provided
        // and merkle proof is valid but doesn't verify
        // any other server parameters

        assertEquals(action.signatures().size, 1)
    }


    @Test
    fun testNoSignatureProvidedByOpenAccount() {
        val action = ActionOpenAccount.newInstance(
            owner = organizer.ownerKeyPair.publicKeyBytes.toPublicKey(),
            type = AccountType.Outgoing,
            accountCluster = organizer.tray.outgoing.getCluster()
        )

        assertEquals(0, action.signatures().size)

        action.serverParameter = basicConfig

        assertEquals(0, action.signatures().size)
    }

    private val basicConfig: ServerParameter = ServerParameter(
        actionId = 0,
        parameter = null,
        configs = listOf(
            ServerParameter.Config(
                nonce = PublicKey.fromBase58(base58 = "JDwJWHij1E75GVAAcMUPkwDgC598wRdF4a7d76QX895S"),
                blockhash = PublicKey.fromBase58(base58 = "BXLEqnSJxMHvEJQHRMSbsFQGDpBn891BpQo828xejbi1")
            )
        )
    )

    private val tempPrivacy: ServerParameter = ServerParameter(
        actionId = 0,
        parameter = ServerParameter.Parameter.TempPrivacy(
            treasury = PublicKey.fromBase58(base58 = "Ddk7k7zMMWsp8fZB12wqbiADdXKQFWfwUUsxSo73JaQ9"),
            recentRoot = PublicKey.fromBase58(base58 = "2sDAFcEZkLd3mbm6SaZhifctkyB4NWsp94GMnfDs1BfR")
        ),
        configs = listOf(
            ServerParameter.Config(
                nonce = PublicKey.fromBase58(base58 = "JDwJWHij1E75GVAAcMUPkwDgC598wRdF4a7d76QX895S"),
                blockhash = PublicKey.fromBase58(base58 = "BXLEqnSJxMHvEJQHRMSbsFQGDpBn891BpQo828xejbi1")
            )
        )
    )

    private val leaf = PublicKey("2ocuvgy8ETZp9WDaEy4rpYz2QyeZ7JAiEvXKbW5rKcd4".decodeBase58().toList())
    private val root = Hash("9EuLAJgnMpEq8wmQUFTNxgYJG2FkAPAGCUhrNK447Uox".decodeBase58().toList())
    private val proof = listOf(
    "4DEt3CHLarXBy74hiJf5t74HmKfTw5DeLK2nzTLFv3Pq",
    "73uNXKLpHkTgc9ubvyRXTGaNUh19TUx8M9bN4PNTn544",
    "2QH34Bqm89sadRqpz1U5M3Cd34xxNLnTHdxzn4LA3EKU",
    "AaySpzaCsgyTVVgUA9bNTNC6sGsws7sTYNyz2oFAe1gT",
    "BFHDwqjAPupY4PoJn5Lvx7t9mQrXy6iGnTS7NuRyrEav",
    "BFy4XgE4j8NW5PxzDMkH7FWXZcMuFb9zoVSBqdjxm21A",
    "B5Lvj9Zdrynu2DGjYXGmxKxRbvmVFtYoGLyqrzZFymXo",
    "GEspB8aMfyV4Hmtt7fGmFXsZ5QWrbBUSfPeDT3dXS7gG",
    "FuVPGmTwWZayoWt4th2dv8X9xEmRrLvqTbdAXXfRi6Ei",
    "CbTZ7BrcBUsmGEjUqxrjkvDkKNRRJHrjF9tTb3mLmMWb",
    "GEoigbUN6rsrrpRdNi5rJgX2YDXmE6gDsLYevSchzcg4",
    "Gb2zXSV9vxhPkem6PrW45rPiEy9dbJ9nFg7ixQEV4JYh",
    "Bb2r8JJdExSAasR38yuTJu2XHRZEGHRxCR71MrJ6nW1z",
    "5zTGsTA9vmzGYwVYeD3MDcehybca93prZRdjVqRzZQ6y",
    "BbH8JeD3emXYkNw3DvLERM3hMPXhgCEqcU132hSo2uH7",
    "F9re8k2sX2BVGX8WqRBGyiZ2aPvvRj4s62jmtgM73hmT",
    "DGFU6XD6eYi3GtVAwYBP4d2DUYv1BGiquijQH6HXLLi4",
    "8TaNzgiEAP4VoXkBjb1toiZ9fw84RhqezdYt3RhNXR3u",
    "BnF8qb2kYZxFHtmqWircb1Di33XTQc8TV17oFwi1tZ4u",
    "BBNfGrQ7cKBcZgQmqCgw45s9QLkx41qcTjYwrn7tAtoM",
    "Dkf4Fpukx558idi6XwnEx9aAu8GLDzYUC3eN7hQQxPsJ",
    "72BxCoqc9cnQvqEZmqzLcZH7VyMBjJFj3R47D8gpV8WL",
    "BSVw2t3RwN4ab9Zpd68pwLqwHVecgHvacZB28QgNQ3L",
    "7YvGe21SSF93mZoyPsgVF6dD78YWCkVwSff9a4EdE3aj",
    "9wZwjy3V8827XZeeE4CxZgXU5WsRGCfcRYHkaPtB7QGb",
    "GQb7NMsiEfwVWxuLgn7Tev1KEZSs4ASayUiULjACtxNv",
    "Wpb4nF5rc9GpSbWXPUNwA31bemJp61HexerhUx97H8B",
    "HwXMPKHXQoBQkM533yqatDbaY3HLDapUWVGVWUv6366a",
    "Wom44oATBqSD7SZpBwHRmkXhKV4qsC7SnneGTLKhvdN",
    "3xmn2hQdDSKN1ompFNh6AwnQBucWK7Z8mJPyXJzTpLb4",
    "HQ5WDTtCvL14aa16UZJStZVVCTcoYbiUayzzBFm8e97r",
    "Af6F2xzEKyjuiy7wjukatK9BzW42K7vXekkZq9C7W3K",
    "ECS1Mcvt2pYJxYkMDNAp4sNjQq4SadsL3KeJx59ATLbo",
    "5B2C3uLH4TvrrriZgo5UbfQwDVbeqqtd7NPwaujfipAd",
    "DLd8jX9r1o57SaBgnqzextfBHb7aSGdL98t3EzhiKXjC",
    "2cz8R5HYZXs5PKhXXXr562go49A4d5gor42Khejz8K2A",
    "B8ucJEMrosxPoSnnBgMbkcmsGWoqusaaheATT8UFa7AF",
    "D7JCQL3FMGkoqfvvP9TBzzirTeNQDHbUYAxA9Di6pkQm",
    "7fiWLao84havAULW9y5mRDpAqCDSFp2hRmDfN7xQWdWk",
    "9SCyBETC2xV1yk44voyQ7MED4SSURpNsuu6MNY12KndN",
    "FQKnR4ngeSet5UfWyzXsf7RFU4QN83T9C5xSJWcHcZVX",
    "DzN3txNccgTDG58PWfgkx9wBuVPShTp1VTrPyHkvnbpq",
    "4cCa2Zen5gn83AJmAgn3mZE3NodVaYaMZM4UykQcTXyy",
    "7XDMh3UsVHVomz4MmsSPcKtEkSwxGA4S5ypUb5t2Dvmv",
    "6VoqDc2CWeg158GDuQTeerh1VWHRFbcjrKo2VeiiXAD7",
    "5uGA5QbthCBe5QiY3BwaqfnwwfJgVahZc8WHPFXwBV2m",
    "AAV7TTewgfmN6FHW3oV3ad3Q6KKcdbP6ijt7SUThD2bN",
    "AD2GHkMgEmtRFWi6HLpZeHKAeRAZUnGPF7h4mVbeUj7o",
    "By3ScwWYWdZAcFXo6V68cRH6kSbdqtUMgNVdnSNevgb5",
    "C2RCxm7ZSd9A2fXEvJguBYNK6oUdVrMSCHB3k2JxBKek",
    "FHvwmz1bJqm6SfeBLriNhSh8wuwEJc4KDxi2PpVfQtqg",
    "R29HU9mCjih74wRWLbW3nXLcUAbEJAqKUrpXENSbziS",
    "AvNJa3vqawjfrGmqKPybWQa4V42uyiigUgchT4gHt5Gf",
    "CozYPv4cdVeRb5QWCrtuVJk3f2rHJeAFD9ZtQjHLqyyW",
    "BeLz3Yqv2ikvQkmH2mxXYGHgvo93Ns77hStxSjuA8UsZ",
    "H4tEGNHHcCLbA963wtzJxsZVuEdrpQwrBBnmWcBAhChs",
    "CUmSAsfdvnJTUkWLA8chums2ffyveiRNkNVu3t6as6N7",
    "62djLqbyFJz3iJiY4NkWvio46dMfP291cWfZ44wdeDgE",
    "4JjYv9v3z2YoBrMCsmXE6abpm5bNTKwuJp99rjoTdmPF",
    "JB7wk6DDiYKjzasvHPpySd2aCjh1UX5adD5eZgazwEUM",
    "3fCTfZwwMipFUpvbXbcDBBtwuo23hmuTJMYaVECWwNTP",
    "CZ94cA7JHBb4a8mqN9xEJquPNX1TxKqL3cBQms6yfgTr",
    "8PxPosHkG5Q6VBnhiimJaH88yPYt6ZDp4szMBfaVTLun",
    ).map { PublicKey(it.decodeBase58().toList()) }

    val privacyUpgrade = ServerParameter(
        actionId = 0,
        parameter = ServerParameter.Parameter.PermanentPrivacyUpgrade(
            newCommitment = mock,
            newCommitmentTranscript =  mock,
            newCommitmentDestination =  mock,
            newCommitmentAmount = Kin.fromKin(1),
            merkleRoot = root,
            merkleProof = proof
        ),
        configs = listOf(
            ServerParameter.Config(
                nonce = PublicKey("JDwJWHij1E75GVAAcMUPkwDgC598wRdF4a7d76QX895S".decodeBase58().toList()),
                blockhash = PublicKey("BXLEqnSJxMHvEJQHRMSbsFQGDpBn891BpQo828xejbi1".decodeBase58().toList())
            )
        )
    )
}