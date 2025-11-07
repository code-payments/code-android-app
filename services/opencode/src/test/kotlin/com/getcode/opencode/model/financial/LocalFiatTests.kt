package com.getcode.opencode.model.financial

import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.opencode.utils.padded
import com.getcode.solana.keys.PublicKey
import java.security.KeyPairGenerator
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertTrue
class LocalFiatTests {

    private val launchpadMetadata = LaunchpadMetadata(
        currencyConfig = generateRandomPublicKeyForTest(),
        liquidityPool = generateRandomPublicKeyForTest(),
        seed = generateRandomPublicKeyForTest(),
        authority = vmAuthority,
        mintVault = generateRandomPublicKeyForTest(),
        coreMintVault = generateRandomPublicKeyForTest(),
        coreMintFees = generateRandomPublicKeyForTest(),
        currentCirculatingSupplyQuarks = 0,
        coreMintLockedQuarks = 0,
        sellFeeBps = 0
    )

    private val token = Token(
        address = vmAuthority,
        decimals = 6,
        name = "USDC",
        symbol = "USDC",
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            authority = vmAuthority,
            vm = vmAuthority,
            lockDurationInDays = TimelockDerivedAccounts.lockoutInDays.toInt()
        ),
        launchpadMetadata = launchpadMetadata,
        billCustomizations = null,
    )

    @Test
    fun `test sending amounts`() {
        val startSupply = 1_00_00_000_000
        val endSupply = 21_000_000_00_00_000_000

        val fiatToTest = listOf(
            5.00.toFiat(),
            10.00.toFiat(),
            100.00.toFiat(),
            500.00.toFiat(),
            1_000.00.toFiat(),
        )

        val output = buildString {
            var supply = startSupply
            while (supply <= endSupply) {
                val updatedTokenOnChain = token.copy(
                    launchpadMetadata = launchpadMetadata.copy(
                        currentCirculatingSupplyQuarks = supply
                    )
                )
                fiatToTest.forEach { amount ->
                    val exchanged = LocalFiat.valueExchangeIn(
                        amount = amount,
                        token = updatedTokenOnChain,
                        rate = Rate.oneToOne,
                        debug = false,
                    )

                    val formattedSupply = supply.toString().padded(20)
                    val underlying = exchanged.underlyingTokenAmount.quarks.toString().padded(20)
                    val converted = exchanged.nativeAmount.formatted().padded(20)

                    appendLine("$formattedSupply $underlying $converted")
                }
                supply *= 10
            }
        }.trim()

        println(output)

        val expectedOutput = """
            10000000000          5001092401997        $5.00               
            10000000000          10004379663394       $10.00              
            10000000000          100441080923772      $100.00             
            10000000000          511295760602584      $500.00             
            10000000000          1046604099690267     $1,000.00           
            100000000000         5001052911980        $5.00               
            100000000000         10004300648691       $10.00              
            100000000000         100440284483693      $100.00             
            100000000000         511291632270217      $500.00             
            100000000000         1046595446081380     $1,000.00           
            1000000000000        5000658028967        $5.00               
            1000000000000        10003510535997       $10.00              
            1000000000000        100432320431767      $100.00             
            1000000000000        511250350821238      $500.00             
            1000000000000        1046508914111068     $1,000.00           
            10000000000000       4996710913709        $5.00               
            10000000000000       9995612841801        $10.00              
            10000000000000       100352714788750      $100.00             
            10000000000000       510837723741344      $500.00             
            10000000000000       1045644006120180     $1,000.00           
            100000000000000      4957410747532        $5.00               
            100000000000000      9916978171984        $10.00              
            100000000000000      99560135637918       $100.00             
            100000000000000      506730134317242      $500.00             
            100000000000000      1037035954468485     $1,000.00           
            1000000000000000     4581018238122        $5.00               
            1000000000000000     9163878032451        $10.00              
            1000000000000000     91971957711638       $100.00             
            1000000000000000     467464270426932      $500.00             
            1000000000000000     954919469210810      $1,000.00           
            10000000000000000    2079970815228        $5.00               
            10000000000000000    4160321190168        $10.00              
            10000000000000000    41671690967489       $100.00             
            10000000000000000    209898607695880      $500.00             
            10000000000000000    423734428251854      $1,000.00           
            100000000000000000   775257916            $5.00               
            100000000000000000   1550515885           $10.00              
            100000000000000000   15505168342          $100.00             
            100000000000000000   77526052595          $500.00             
            100000000000000000   155052632401         $1,000.00
        """.trimIndent()

        val actualLines = output.lines()
        val expectedLines = expectedOutput.lines()

        assertEquals(expectedLines.size, actualLines.size)

        for (i in actualLines.indices) {
            val actualParts = actualLines[i].split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val expectedParts = expectedLines[i].split("\\s+".toRegex()).filter { it.isNotEmpty() }

            assertEquals(expectedParts[0], actualParts[0], "Column 1 mismatch on line $i")
            val diff = (expectedParts[1].toLong() - actualParts[1].toLong()).let { if (it < 0) -it else it }
            assertTrue(diff <= 1, "Column 2 is not within 1 on line $i")
            assertEquals(expectedParts[2], actualParts[2], "Column 3 mismatch on line $i")
        }
    }

    @Test
    fun `test quarks to balance conversion`() {
        val startVol = 1_000_000L
        val endVol = 100_000_000_000_000L

        val quarks = 1_000_000_000_000L

        val output = buildString {
            var index = 0
            var volume = startVol
            while (volume <= endVol) {
                val updatedTokenOnChain = token.copy(
                    launchpadMetadata = launchpadMetadata.copy(
                        coreMintLockedQuarks = volume
                    )
                )
                val tokenBalance = Fiat.tokenBalance(
                    quarks = quarks,
                    token = updatedTokenOnChain,
                )

                val exchanged = LocalFiat.valueExchangeIn(
                    amount = tokenBalance,
                    token = updatedTokenOnChain,
                    rate = Rate.oneToOne,
                    debug = false,
                )

                val nativeAmountsForExchangedQuarks = exchanged.nativeAmount.formatted()

                appendLine(nativeAmountsForExchangedQuarks)

                volume *= 10
                index += 1
            }
        }.trim()

        println(output)

        val expectedOutput = listOf(
            "$1.00",
            "$1.00",
            "$1.01",
            "$1.09",
            "$1.88",
            "$9.77",
            "$88.71",
            "$878.14",
            "$8,772.37"
        )

        val generatedOutput = output.lines()

        assertEquals(expectedOutput, generatedOutput)
    }
}

/**
 * Generates a random Public Key for testing purposes.
 */
private fun generateRandomPublicKeyForTest(): PublicKey {
    // 1. Generate a KeyPair
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048, SecureRandom()) // Use SecureRandom for strong keys
    val keyPair = keyGen.generateKeyPair()

    // 2. Extract the public key bytes
    val publicKeyBytes = keyPair.public.encoded.toList()

    return PublicKey(publicKeyBytes)
}