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
}
