package com.getcode.opencode.internal.transactors

import com.getcode.opencode.model.accounts.AccountInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class ClaimValidationTest {

    private fun accountInfo(
        claimState: AccountInfo.ClaimState = AccountInfo.ClaimState.NotClaimed,
        isGiftCardIssuer: Boolean = false,
    ): AccountInfo = mockk(relaxed = true) {
        every { this@mockk.claimState } returns claimState
        every { this@mockk.isGiftCardIssuer } returns isGiftCardIssuer
    }

    // region claimState checks

    @Test
    fun `NotClaimed and not issuer returns null (eligible)`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(claimState = AccountInfo.ClaimState.NotClaimed),
            claimIfOwned = false,
        )
        assertNull(result)
    }

    @Test
    fun `Claimed returns AlreadyClaimed`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(claimState = AccountInfo.ClaimState.Claimed),
            claimIfOwned = false,
        )
        assertIs<ReceiveGiftTransactorError.AlreadyClaimed>(result)
    }

    @Test
    fun `Expired returns Expired`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(claimState = AccountInfo.ClaimState.Expired),
            claimIfOwned = false,
        )
        assertIs<ReceiveGiftTransactorError.Expired>(result)
    }

    @Test
    fun `Unknown claimState returns Expired`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(claimState = AccountInfo.ClaimState.Unknown),
            claimIfOwned = false,
        )
        assertIs<ReceiveGiftTransactorError.Expired>(result)
    }

    // endregion

    // region issuer checks

    @Test
    fun `issuer with claimIfOwned false returns UsersGiftCard`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(
                claimState = AccountInfo.ClaimState.NotClaimed,
                isGiftCardIssuer = true,
            ),
            claimIfOwned = false,
        )
        assertIs<ReceiveGiftTransactorError.UsersGiftCard>(result)
    }

    @Test
    fun `issuer with claimIfOwned true returns null (eligible)`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(
                claimState = AccountInfo.ClaimState.NotClaimed,
                isGiftCardIssuer = true,
            ),
            claimIfOwned = true,
        )
        assertNull(result)
    }

    @Test
    fun `non-issuer with claimIfOwned false returns null (eligible)`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(
                claimState = AccountInfo.ClaimState.NotClaimed,
                isGiftCardIssuer = false,
            ),
            claimIfOwned = false,
        )
        assertNull(result)
    }

    // endregion

    // region priority (claimState checked before issuer)

    @Test
    fun `Claimed takes priority over issuer check`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(
                claimState = AccountInfo.ClaimState.Claimed,
                isGiftCardIssuer = true,
            ),
            claimIfOwned = false,
        )
        assertIs<ReceiveGiftTransactorError.AlreadyClaimed>(result)
    }

    @Test
    fun `Expired takes priority over issuer check`() {
        val result = ReceiveGiftCardTransactor.validateClaimEligibility(
            info = accountInfo(
                claimState = AccountInfo.ClaimState.Expired,
                isGiftCardIssuer = true,
            ),
            claimIfOwned = true,
        )
        assertIs<ReceiveGiftTransactorError.Expired>(result)
    }

    // endregion
}
