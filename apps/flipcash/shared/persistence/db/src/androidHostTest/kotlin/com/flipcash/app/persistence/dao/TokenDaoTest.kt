package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.embedded.LaunchpadMetadataEmbedded
import com.flipcash.app.persistence.embedded.VmMetadataEmbedded
import com.flipcash.app.persistence.entities.TokenEntity
import com.flipcash.app.persistence.entities.TokenValuationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TokenDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: TokenDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tokenDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -- helpers --

    private fun makeToken(
        address: String = "mint_${System.nanoTime()}",
        name: String = "TestToken",
        symbol: String = "TT",
        decimals: Int = 9,
    ) = TokenEntity(
        address = address,
        decimals = decimals,
        name = name,
        symbol = symbol,
        createdAt = System.currentTimeMillis(),
        description = "A test token",
        imageUrl = "https://example.com/token.png",
        vmMetadata = VmMetadataEmbedded(
            vm = "vm_address",
            authority = "authority_address",
            lockDurationInDays = 7,
        ),
        launchpadMetadata = LaunchpadMetadataEmbedded(
            currencyConfig = "cc_address",
            liquidityPool = "lp_address",
            seed = "seed_address",
            authority = "lp_authority",
            mintVault = "mv_address",
            coreMintVault = "cmv_address",
            currentCirculatingSupplyQuarks = 1_000_000L,
            sellFeeBps = 100,
            priceAmount = 0.01,
            marketCapAmount = 100_000.0,
        ),
        socialLinks = null,
        billCustomizationsJson = null,
        holderMetricsJson = null,
    )

    private fun makeValuation(
        tokenAddress: String,
        balanceQuarks: Long = 500_000L,
        costBasis: Double = 0.05,
    ) = TokenValuationEntity(
        tokenAddress = tokenAddress,
        balanceQuarks = balanceQuarks,
        costBasis = costBasis,
    )

    // -- tests --

    @Test
    fun `upsertTokens inserts and getAll retrieves`() = runTest {
        val token1 = makeToken(address = "mint_aaa")
        val token2 = makeToken(address = "mint_bbb")

        dao.upsertTokens(token1, token2)
        val all = dao.getAll()

        assertEquals(2, all.size)
        assertTrue(all.any { it.address == "mint_aaa" })
        assertTrue(all.any { it.address == "mint_bbb" })
    }

    @Test
    fun `getByMint returns correct token`() = runTest {
        val token = makeToken(address = "mint_target", name = "Target")
        val other = makeToken(address = "mint_other", name = "Other")

        dao.upsertTokens(token, other)
        val result = dao.getByMint("mint_target")

        assertNotNull(result)
        assertEquals("Target", result.name)
        assertEquals("mint_target", result.address)
    }

    @Test
    fun `getByMint returns null for missing mint`() = runTest {
        val result = dao.getByMint("nonexistent")
        assertNull(result)
    }

    @Test
    fun `upsertTokens with existing key replaces`() = runTest {
        val original = makeToken(address = "mint_dup", name = "Original", symbol = "OG")
        dao.upsertTokens(original)

        val updated = original.copy(name = "Updated", symbol = "UP")
        dao.upsertTokens(updated)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("Updated", all.first().name)
        assertEquals("UP", all.first().symbol)
    }

    @Test
    fun `clearTokens removes all entries`() = runTest {
        dao.upsertTokens(makeToken(address = "a"), makeToken(address = "b"))
        assertEquals(2, dao.getAll().size)

        dao.clearTokens()

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `upsertBalances and getBalance roundtrip`() = runTest {
        val token = makeToken(address = "mint_bal")
        dao.upsertTokens(token)

        val valuation = makeValuation(tokenAddress = "mint_bal", balanceQuarks = 999L, costBasis = 1.23)
        dao.upsertBalances(valuation)

        val result = dao.getBalance("mint_bal")
        assertNotNull(result)
        assertEquals(999L, result.balanceQuarks)
        assertEquals(1.23, result.costBasis)
    }

    @Test
    fun `getTokensWithBalances returns joined results`() = runTest {
        val token1 = makeToken(address = "mint_j1")
        val token2 = makeToken(address = "mint_j2")
        dao.upsertTokens(token1, token2)

        // Only token1 has a valuation
        dao.upsertBalances(makeValuation(tokenAddress = "mint_j1", balanceQuarks = 42L))

        val results = dao.getTokensWithBalances()
        assertEquals(2, results.size)

        val withBalance = results.first { it.token.address == "mint_j1" }
        assertNotNull(withBalance.valuation)
        assertEquals(42L, withBalance.valuation!!.balanceQuarks)

        val withoutBalance = results.first { it.token.address == "mint_j2" }
        assertNull(withoutBalance.valuation)
    }

    @Test
    fun `observeAll emits on insert`() = runTest {
        dao.observeAll().test {
            // Initial emission should be empty
            assertEquals(emptyList(), awaitItem())

            dao.upsertTokens(makeToken(address = "mint_obs1"))
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("mint_obs1", first.first().address)

            dao.upsertTokens(makeToken(address = "mint_obs2"))
            val second = awaitItem()
            assertEquals(2, second.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll removes both tokens and balances`() = runTest {
        val token = makeToken(address = "mint_ca")
        dao.upsertTokens(token)
        dao.upsertBalances(makeValuation(tokenAddress = "mint_ca"))

        assertEquals(1, dao.getAll().size)
        assertEquals(1, dao.getAllBalances().size)

        dao.clearAll()

        assertTrue(dao.getAll().isEmpty())
        assertTrue(dao.getAllBalances().isEmpty())
    }
}
