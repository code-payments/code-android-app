package com.getcode.opencode.internal.solana.extensions

import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PdaDerivationTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    // --- deriveAssociatedAccount ---

    @Test
    fun associatedAccountIsDeterministic() {
        val owner = testKey(1)
        val mint = testKey(2)
        val result1 = PublicKey.deriveAssociatedAccount(owner, mint)
        val result2 = PublicKey.deriveAssociatedAccount(owner, mint)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun associatedAccountBumpInRange() {
        val owner = testKey(1)
        val mint = testKey(2)
        val result = PublicKey.deriveAssociatedAccount(owner, mint)
        assertTrue(result.bump in 0..255)
    }

    @Test
    fun associatedAccountIsOffCurve() {
        val owner = testKey(1)
        val mint = testKey(2)
        val result = PublicKey.deriveAssociatedAccount(owner, mint)
        assertTrue(!Ed25519.onCurve(result.publicKey.bytes.toByteArray()))
    }

    @Test
    fun associatedAccountDiffersForDifferentOwners() {
        val mint = testKey(2)
        val result1 = PublicKey.deriveAssociatedAccount(testKey(1), mint)
        val result2 = PublicKey.deriveAssociatedAccount(testKey(3), mint)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun associatedAccountDiffersForDifferentMints() {
        val owner = testKey(1)
        val result1 = PublicKey.deriveAssociatedAccount(owner, testKey(2))
        val result2 = PublicKey.deriveAssociatedAccount(owner, testKey(3))
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveVirtualMachineAccount ---

    @Test
    fun vmAccountIsDeterministic() {
        val mint = testKey(1)
        val authority = testKey(2)
        val result1 = PublicKey.deriveVirtualMachineAccount(mint, authority, 21u)
        val result2 = PublicKey.deriveVirtualMachineAccount(mint, authority, 21u)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun vmAccountDiffersForDifferentLockout() {
        val mint = testKey(1)
        val authority = testKey(2)
        val result1 = PublicKey.deriveVirtualMachineAccount(mint, authority, 21u)
        val result2 = PublicKey.deriveVirtualMachineAccount(mint, authority, 7u)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun vmAccountIsOffCurve() {
        val mint = testKey(1)
        val authority = testKey(2)
        val result = PublicKey.deriveVirtualMachineAccount(mint, authority, 21u)
        assertTrue(!Ed25519.onCurve(result.publicKey.bytes.toByteArray()))
    }

    // --- deriveDepositAccount ---

    @Test
    fun depositAccountIsDeterministic() {
        val vm = testKey(1)
        val depositor = testKey(2)
        val result1 = PublicKey.deriveDepositAccount(vm, depositor)
        val result2 = PublicKey.deriveDepositAccount(vm, depositor)
        assertEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun depositAccountDiffersForDifferentDepositors() {
        val vm = testKey(1)
        val result1 = PublicKey.deriveDepositAccount(vm, testKey(2))
        val result2 = PublicKey.deriveDepositAccount(vm, testKey(3))
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveTimelockStateAccount ---

    @Test
    fun timelockStateIsDeterministic() {
        val owner = testKey(1)
        val mint = Mint(ByteArray(32) { 2 }.toList())
        val authority = testKey(3)
        val result1 = PublicKey.deriveTimelockStateAccount(owner, mint, authority, 21u)
        val result2 = PublicKey.deriveTimelockStateAccount(owner, mint, authority, 21u)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun timelockStateDiffersForDifferentOwners() {
        val mint = Mint(ByteArray(32) { 2 }.toList())
        val authority = testKey(3)
        val result1 = PublicKey.deriveTimelockStateAccount(testKey(1), mint, authority, 21u)
        val result2 = PublicKey.deriveTimelockStateAccount(testKey(4), mint, authority, 21u)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveTimelockVaultAccount ---

    @Test
    fun timelockVaultIsDeterministic() {
        val stateAccount = testKey(1)
        val result1 = PublicKey.deriveTimelockVaultAccount(stateAccount, 0)
        val result2 = PublicKey.deriveTimelockVaultAccount(stateAccount, 0)
        assertEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun timelockVaultDiffersForDifferentVersions() {
        val stateAccount = testKey(1)
        val result1 = PublicKey.deriveTimelockVaultAccount(stateAccount, 0)
        val result2 = PublicKey.deriveTimelockVaultAccount(stateAccount, 1)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveVmOmnibusAddress ---

    @Test
    fun vmOmnibusIsDeterministic() {
        val vm = testKey(1)
        val result1 = PublicKey.deriveVmOmnibusAddress(vm)
        val result2 = PublicKey.deriveVmOmnibusAddress(vm)
        assertEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun vmOmnibusDiffersForDifferentVm() {
        val result1 = PublicKey.deriveVmOmnibusAddress(testKey(1))
        val result2 = PublicKey.deriveVmOmnibusAddress(testKey(2))
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveSwapAddress ---

    @Test
    fun swapAddressIsDeterministic() {
        val owner = testKey(1)
        val mint = testKey(2)
        val authority = testKey(3)
        val result1 = PublicKey.deriveSwapAddress(owner, mint, authority, 21u)
        val result2 = PublicKey.deriveSwapAddress(owner, mint, authority, 21u)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun swapAddressDiffersForDifferentOwners() {
        val mint = testKey(2)
        val authority = testKey(3)
        val result1 = PublicKey.deriveSwapAddress(testKey(1), mint, authority, 21u)
        val result2 = PublicKey.deriveSwapAddress(testKey(4), mint, authority, 21u)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveCoinbasePoolAddress ---

    @Test
    fun coinbasePoolIsDeterministic() {
        val result1 = PublicKey.deriveCoinbasePoolAddress()
        val result2 = PublicKey.deriveCoinbasePoolAddress()
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun coinbasePoolIsOffCurve() {
        val result = PublicKey.deriveCoinbasePoolAddress()
        assertTrue(!Ed25519.onCurve(result.publicKey.bytes.toByteArray()))
    }

    // --- deriveCoinbaseTokenVaultAddress ---

    @Test
    fun coinbaseTokenVaultIsDeterministic() {
        val pool = testKey(1)
        val mint = testKey(2)
        val result1 = PublicKey.deriveCoinbaseTokenVaultAddress(pool, mint)
        val result2 = PublicKey.deriveCoinbaseTokenVaultAddress(pool, mint)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun coinbaseTokenVaultDiffersForDifferentMints() {
        val pool = testKey(1)
        val result1 = PublicKey.deriveCoinbaseTokenVaultAddress(pool, testKey(2))
        val result2 = PublicKey.deriveCoinbaseTokenVaultAddress(pool, testKey(3))
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    @Test
    fun coinbaseTokenVaultDiffersForDifferentPools() {
        val mint = testKey(2)
        val result1 = PublicKey.deriveCoinbaseTokenVaultAddress(testKey(1), mint)
        val result2 = PublicKey.deriveCoinbaseTokenVaultAddress(testKey(3), mint)
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveCoinbaseVaultTokenAccountAddress ---

    @Test
    fun coinbaseVaultTokenAccountIsDeterministic() {
        val vault = testKey(1)
        val result1 = PublicKey.deriveCoinbaseVaultTokenAccountAddress(vault)
        val result2 = PublicKey.deriveCoinbaseVaultTokenAccountAddress(vault)
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun coinbaseVaultTokenAccountDiffersForDifferentVaults() {
        val result1 = PublicKey.deriveCoinbaseVaultTokenAccountAddress(testKey(1))
        val result2 = PublicKey.deriveCoinbaseVaultTokenAccountAddress(testKey(2))
        assertNotEquals(result1.publicKey, result2.publicKey)
    }

    // --- deriveCoinbaseWhitelistAddress ---

    @Test
    fun coinbaseWhitelistIsDeterministic() {
        val result1 = PublicKey.deriveCoinbaseWhitelistAddress()
        val result2 = PublicKey.deriveCoinbaseWhitelistAddress()
        assertEquals(result1.publicKey, result2.publicKey)
        assertEquals(result1.bump, result2.bump)
    }

    @Test
    fun coinbaseWhitelistIsOffCurve() {
        val result = PublicKey.deriveCoinbaseWhitelistAddress()
        assertTrue(!Ed25519.onCurve(result.publicKey.bytes.toByteArray()))
    }

    // --- Coinbase PDA chain: pool -> vault -> vaultTokenAccount ---

    @Test
    fun coinbasePdaChainProducesDistinctAddresses() {
        val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
        val mint = testKey(10)
        val vault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, mint).publicKey
        val vaultTA = PublicKey.deriveCoinbaseVaultTokenAccountAddress(vault).publicKey
        val whitelist = PublicKey.deriveCoinbaseWhitelistAddress().publicKey

        // All four should be distinct
        val all = setOf(pool, vault, vaultTA, whitelist)
        assertEquals(4, all.size, "pool, vault, vaultTA, and whitelist should all be distinct")
    }

    // --- Known value: well-known associated token address ---

    @Test
    fun associatedAccountForKnownMintProducesExpectedLength() {
        // USDC mint on Solana: EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
        val usdcMintBytes = Base58.decode("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        val usdcMint = PublicKey(usdcMintBytes.toList())

        val ownerBytes = Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR")
        val owner = PublicKey(ownerBytes.toList())

        val result = PublicKey.deriveAssociatedAccount(owner, usdcMint)
        assertEquals(32, result.publicKey.bytes.size)
        assertTrue(result.bump in 0..255)
    }
}
