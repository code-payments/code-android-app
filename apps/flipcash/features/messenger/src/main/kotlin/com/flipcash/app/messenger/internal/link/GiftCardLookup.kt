package com.flipcash.app.messenger.internal.link

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.internal.transactors.AccountClusterFactory
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

/**
 * Reads a gift card's account info for a card. Read-only, by construction: the claim path is
 * `BillController.receiveGiftCard`, and this class has no `BillController`. The derivation
 * mirrors `ReceiveGiftCardTransactor.with`, and the query is the one that transactor already
 * makes before its pre-claim checks — this stops where it starts checking.
 */
internal class GiftCardLookup @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val accountClusterFactory: AccountClusterFactory,
    private val accountController: AccountController,
    private val tokenCoordinator: TokenCoordinator,
    private val userManager: UserManager,
) {
    suspend operator fun invoke(entropy: String): Result<LinkCardResolver.Snapshot> = runCatching {
        val viewer = requireNotNull(userManager.accountCluster) { "signed out" }
        val mnemonic = mnemonicManager.fromEntropyBase58(entropy)
        val giftCardOwner = accountClusterFactory.create(
            DerivedKey.derive(DerivePath.primary, mnemonic = mnemonic),
        )

        val info = accountController
            .getAccounts(accountOwner = giftCardOwner, requestingOwner = viewer)
            .getOrThrow()
            .accounts.values.first()

        // Same lookup the chat mapper already does for a cash bubble's token name and icon.
        val token = tokenCoordinator.getTokenMetadata(info.mint).getOrNull()?.token

        LinkCardResolver.Snapshot(
            amount = info.originalExchangeData.let { data ->
                Fiat(
                    fiat = data.nativeAmount,
                    currencyCode = CurrencyCode.tryValueOf(data.currencyCode) ?: CurrencyCode.USD,
                ).formatted()
            },
            // Mirrors ReceiveGiftCardTransactor.validateClaimEligibility, which treats Unknown as
            // expired. Mapping it to Claimable instead would put "claim" on a card the claim path
            // refuses -- the one disagreement between the card and the tap that users would see.
            claim = when (info.claimState) {
                AccountInfo.ClaimState.Claimed -> LinkCard.Cash.Claim.Claimed
                AccountInfo.ClaimState.Expired -> LinkCard.Cash.Claim.Expired
                AccountInfo.ClaimState.Unknown -> LinkCard.Cash.Claim.Expired
                AccountInfo.ClaimState.NotClaimed -> LinkCard.Cash.Claim.Claimable
            },
            tokenSymbol = token?.symbol?.takeIf { it.isNotBlank() } ?: token?.name.orEmpty(),
            iconUrl = token?.imageUrl?.takeIf { it.isNotBlank() },
            issuedByViewer = info.isGiftCardIssuer,
        )
    }
}
