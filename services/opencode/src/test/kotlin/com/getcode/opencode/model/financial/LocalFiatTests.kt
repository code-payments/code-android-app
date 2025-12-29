package com.getcode.opencode.model.financial

import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.opencode.tests.generateRandomPublicKeyForTest
import com.getcode.opencode.utils.padded
import com.getcode.solana.keys.PublicKey
import java.security.KeyPairGenerator
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertTrue
import kotlin.time.Clock

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
        createdAt = Clock.System.now(),
    )

    @Test
    fun `test sending amounts`() {
        val startValue = 1_000_000L
        val endValue = 100_000_000_000_000L

        val fiatToTest = listOf(
            5.00.toFiat(),
            10.00.toFiat(),
            100.00.toFiat(),
            500.00.toFiat(),
            1_000.00.toFiat(),
        )

        val output = buildString {
            var valueLocked = startValue
            while (valueLocked <= endValue) {
                val updatedTokenOnChain = token.copy(
                    launchpadMetadata = launchpadMetadata.copy(
                        coreMintLockedQuarks = valueLocked
                    )
                )
                fiatToTest.forEach { amount ->
                    val exchanged = LocalFiat.valueExchangeIn(
                        amount = amount,
                        token = updatedTokenOnChain,
                        rate = Rate.oneToOne,
                        debug = false,
                        trace = false,
                    )

                    val formattedLockedValue = valueLocked.toString().padded(20)
                    val underlying = exchanged.underlyingTokenAmount.quarks.toString().padded(20)
                    val converted = exchanged.nativeAmount.formatted().padded(20)

                    appendLine("$formattedLockedValue $underlying $converted")
                }
                valueLocked *= 10
            }
        }.trim()

        println(output)

        val expectedOutput = """
            1000000              5000658048209        $5.00               
            1000000              10003510574497       $10.00              
            1000000              100432320819833      $100.00             
            1000000              511250352832763      $500.00             
            1000000              1046508918327512     $1,000.00           
            10000000             4996712835333        $5.00               
            10000000             9995616686734        $10.00              
            10000000             100352753544040      $100.00             
            10000000             510837924622184      $500.00             
            10000000             1045644427178339     $1,000.00           
            100000000            4957600405006        $5.00               
            100000000            9917357651988        $10.00              
            100000000            99563960395722       $100.00             
            100000000            506749953488320      $500.00             
            100000000            1037077480269837     $1,000.00           
            1000000000           4597708679970        $5.00               
            1000000000           9197272362330        $10.00              
            1000000000           92308339982136       $100.00             
            1000000000           469202608629475      $500.00             
            1000000000           958548327430505      $1,000.00           
            10000000000          2663887739947        $5.00               
            10000000000          5328398095088        $10.00              
            10000000000          53396384541881       $100.00             
            10000000000          269518606881772      $500.00             
            10000000000          545563645468226      $1,000.00           
            100000000000         511690414900         $5.00               
            100000000000         1023403797655        $10.00              
            100000000000         10238174542457       $100.00             
            100000000000         51283066886112       $500.00             
            100000000000         102797869580410      $1,000.00           
            1000000000000        56358788487          $5.00               
            1000000000000        112717855594         $10.00              
            1000000000000        1127228710628        $100.00             
            1000000000000        5637258461866        $500.00             
            1000000000000        11277305850369       $1,000.00           
            10000000000000       5693625634           $5.00               
            10000000000000       11387254111          $10.00              
            10000000000000       113873052964         $100.00             
            10000000000000       569376639559         $500.00             
            10000000000000       1138781717663        $1,000.00           
            100000000000000      569946546            $5.00               
            100000000000000      1139893121           $10.00              
            100000000000000      11398936344          $100.00             
            100000000000000      56994795699          $500.00             
            100000000000000      113989876341         $1,000.00
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
                    trace = false,
                )

                val nativeAmountsForExchangedQuarks = exchanged.nativeAmount.formatted()

                appendLine(nativeAmountsForExchangedQuarks)

                volume *= 10
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