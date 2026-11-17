package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.StatefulSwapResponse
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.StatelessSwapResponse
import com.getcode.opencode.model.transactions.SolanaTransactionConfig
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.solana.keys.PublicKey
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The v1 swap fields (ocp #72) are carried from the protobuf-java server parameters into the
 * domain types, and stay null or empty when the server sends v0 parameters.
 */
class SwapServerParametersMappingTest {

    private fun key(n: Int) = ByteArray(32) { n.toByte() }
    private fun accountId(n: Int): Model.SolanaAccountId =
        Model.SolanaAccountId.newBuilder().setValue(ByteString.copyFrom(key(n))).build()
    private fun blockhash(n: Int): Model.Blockhash =
        Model.Blockhash.newBuilder().setValue(ByteString.copyFrom(key(n))).build()
    private fun publicKey(n: Int) = PublicKey(key(n).toList())

    private val configProto: Model.SolanaTransactionConfig = Model.SolanaTransactionConfig.newBuilder()
        .setComputeUnitLimit(200_000)
        .setLoadedAccountsDataSizeLimit(65_536)
        .setPriorityFeeLamports(5_000)
        .setHeapSize(65_536)
        .build()
    private val config = SolanaTransactionConfig(
        computeUnitLimit = 200_000,
        loadedAccountsDataSizeLimit = 65_536,
        priorityFeeLamports = 5_000,
        heapSize = 65_536,
    )

    private fun existingCurrency() =
        StatefulSwapResponse.ServerParameters.ReserveExistingCurrencyServerParameters.newBuilder()
            .setPayer(accountId(1))
            .setNonce(accountId(2))
            .setBlockhash(blockhash(3))
            .setMemoryAccount(accountId(4))

    private fun newCurrency() =
        StatefulSwapResponse.ServerParameters.ReserveNewCurrencyServerParameter.newBuilder()
            .setPayer(accountId(1))
            .setNonce(accountId(2))
            .setBlockhash(blockhash(3))
            .setAuthority(accountId(4))
            .setSeed(accountId(5))
            .setFeeDestination(accountId(6))

    private fun statefulStablecoin() =
        StatefulSwapResponse.ServerParameters.CoinbaseStableSwapperServerParameter.newBuilder()
            .setPayer(accountId(1))
            .setNonce(accountId(2))
            .setBlockhash(blockhash(3))
            .setFeeDestination(accountId(4))
            .setPoolFeeRecipient(accountId(5))

    private fun statelessStablecoin() =
        StatelessSwapResponse.ServerParameters.CoinbaseStableSwapperServerParameter.newBuilder()
            .setPayer(accountId(1))
            .setBlockhash(blockhash(2))
            .setPoolFeeRecipient(accountId(3))

    @Test
    fun `v0 parameters leave the v1 fields empty`() {
        assertNull(existingCurrency().build().toProps().transactionConfig)
        assertNull(statefulStablecoin().build().toProps().transactionConfig)
        assertNull(statelessStablecoin().build().toProps().transactionConfig)

        val newCurrency = assertIs<StatefulSwapResponseServerParameters.NewCurrency>(newCurrency().build().toProps())
        assertNull(newCurrency.transactionConfig)
        assertTrue(newCurrency.memoryAccounts.isEmpty())
        assertNull(newCurrency.feeAccountOwner)
    }

    @Test
    fun `existing currency carries the transaction config`() {
        val props = existingCurrency().setTransactionConfig(configProto).build().toProps()

        assertEquals(config, props.transactionConfig)
    }

    @Test
    fun `stablecoin parameters carry the transaction config`() {
        assertEquals(config, statefulStablecoin().setTransactionConfig(configProto).build().toProps().transactionConfig)
        assertEquals(config, statelessStablecoin().setTransactionConfig(configProto).build().toProps().transactionConfig)
    }

    @Test
    fun `new currency carries the config, memory accounts and fee account owner`() {
        val memoryAccount =
            StatefulSwapResponse.ServerParameters.ReserveNewCurrencyServerParameter.VmMemoryAccountInitialization.newBuilder()
                .setAddress(accountId(7))
                .setName("memory-0")
                .setNumAccounts(1_024)
                .setAccountSize(72)
                .addAllResizeSizes(listOf(10_240, 20_480, 73_728))
                .setRentTopUpLamports(500_000_000)
                .build()

        val props = newCurrency()
            .setTransactionConfig(configProto)
            .addMemoryAccounts(memoryAccount)
            .setFeeAccountOwner(accountId(8))
            .build()
            .toProps()

        val newCurrency = assertIs<StatefulSwapResponseServerParameters.NewCurrency>(props)
        assertEquals(config, newCurrency.transactionConfig)
        assertEquals(
            listOf(
                StatefulSwapResponseServerParameters.NewCurrency.VmMemoryAccountInitialization(
                    address = publicKey(7),
                    name = "memory-0",
                    numAccounts = 1_024,
                    accountSize = 72,
                    resizeSizes = listOf(10_240, 20_480, 73_728),
                    rentTopUpLamports = 500_000_000,
                ),
            ),
            newCurrency.memoryAccounts,
        )
        assertEquals(publicKey(8), newCurrency.feeAccountOwner)
    }
}
