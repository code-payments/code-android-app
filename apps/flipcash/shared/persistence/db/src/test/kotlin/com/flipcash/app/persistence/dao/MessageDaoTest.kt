package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.MessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the "added money" onboarding milestone, which is really "has any money ever come in" —
 * a buy, a deposit, or a tip received all satisfy it; outgoing entries do not.
 */
@RunWith(RobolectricTestRunner::class)
class MessageDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.messageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -- helpers --

    private var nextId = 0

    private fun metadataJson(type: String) =
        """{"type":"com.flipcash.app.core.feed.MessageMetadata.$type"}"""

    private fun message(
        metadataType: String,
        state: String = "COMPLETED",
    ) = MessageEntity(
        // Base58 alphabet only — the entity decodes this lazily, but keep it valid anyway.
        idBase58 = "message${++nextId}",
        text = "test",
        amountUsdc = 100L,
        amountNative = null,
        nativeCurrency = null,
        rate = null,
        state = state,
        timestamp = nextId.toLong(),
        metadata = metadataJson(metadataType),
        mintBase58 = null,
    )

    /**
     * A convert row as it is actually persisted: `mintBase58` is the *source* mint (the side the
     * feed amount comes from) and the destination mint lives only inside the metadata JSON.
     */
    private fun swapMessage(source: String, destination: String) = MessageEntity(
        idBase58 = "message${++nextId}",
        text = "Converted",
        amountUsdc = 100L,
        amountNative = 100L,
        nativeCurrency = "usd",
        rate = 1.0,
        state = "COMPLETED",
        timestamp = nextId.toLong(),
        metadata = """{"type":"com.flipcash.app.core.feed.MessageMetadata.SwappedCrypto",""" +
            """"swap":{"from":{"underlyingTokenAmount":{"quarks":100},""" +
            """"nativeAmount":{"quarks":100},"rate":{"fx":1.0,"currency":"usd"},"mint":"$source"},""" +
            """"toMint":"$destination","toAmount":null,"fee":{"quarks":10},"swapState":"SUCCEEDED"}}""",
        mintBase58 = source,
    )

    /** A withdrawal executed as a swap: the destination leaves the app, so it is not a convert. */
    private fun withdrawViaSwapMessage(source: String, destination: String) =
        swapMessage(source, destination).let {
            it.copy(
                text = "Withdrew",
                metadata = it.metadata!!.replace(
                    "MessageMetadata.SwappedCrypto\",\"swap\"",
                    "MessageMetadata.WithdrewCrypto\",\"swapMetadata\"",
                ),
            )
        }

    private suspend fun recentForMint(mint: String): List<MessageEntity> {
        var result = emptyList<MessageEntity>()
        dao.observeRecentForMint(mint, limit = 10).test {
            result = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    private suspend fun hasReceivedMoney(): Boolean {
        var result = false
        dao.hasEverReceivedMoney().test {
            result = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    // -- tests --

    @Test
    fun `no messages means no money has come in`() = runTest {
        assertFalse(hasReceivedMoney())
    }

    @Test
    fun `a completed deposit satisfies the milestone`() = runTest {
        dao.upsert(message("DepositedCrypto"))
        assertTrue(hasReceivedMoney())
    }

    @Test
    fun `a completed buy satisfies the milestone`() = runTest {
        dao.upsert(message("BoughtToken"))
        assertTrue(hasReceivedMoney())
    }

    @Test
    fun `a received tip satisfies the milestone`() = runTest {
        dao.upsert(message("ReceivedCrypto"))
        assertTrue(hasReceivedMoney())
    }

    @Test
    fun `outgoing activity alone does not satisfy the milestone`() = runTest {
        dao.upsert(
            message("DirectlySentCrypto"),
            message("IndirectlySentCrypto"),
            message("WithdrewCrypto"),
            message("SoldToken"),
            message("SwappedCrypto"),
            message("PaidCrypto"),
        )
        assertFalse(hasReceivedMoney())
    }

    @Test
    fun `an incoming entry that has not completed does not satisfy the milestone`() = runTest {
        dao.upsert(message("ReceivedCrypto", state = "PENDING"))
        assertFalse(hasReceivedMoney())
    }

    @Test
    fun `the milestone flips live as an incoming entry lands`() = runTest {
        dao.hasEverReceivedMoney().test {
            assertFalse(awaitItem())
            dao.upsert(message("ReceivedCrypto"))
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -- per-token activity --

    private val sourceMint = "5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ"
    private val destinationMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    private val unrelatedMint = "So11111111111111111111111111111111111111112"

    @Test
    fun `a convert shows on the source token's activity`() = runTest {
        dao.upsert(swapMessage(source = sourceMint, destination = destinationMint))
        assertEquals(1, recentForMint(sourceMint).size)
    }

    @Test
    fun `a convert shows on the destination token's activity`() = runTest {
        dao.upsert(swapMessage(source = sourceMint, destination = destinationMint))
        assertEquals(1, recentForMint(destinationMint).size)
    }

    @Test
    fun `a convert stays off an unrelated token's activity`() = runTest {
        dao.upsert(swapMessage(source = sourceMint, destination = destinationMint))
        assertEquals(0, recentForMint(unrelatedMint).size)
    }

    @Test
    fun `a withdrawal executed as a swap stays off the destination token's activity`() = runTest {
        dao.upsert(withdrawViaSwapMessage(source = sourceMint, destination = destinationMint))
        assertEquals(0, recentForMint(destinationMint).size)
        assertEquals(1, recentForMint(sourceMint).size)
    }
}
