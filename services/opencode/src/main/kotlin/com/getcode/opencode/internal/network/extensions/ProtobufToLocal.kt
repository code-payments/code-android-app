package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.destinationOrNull
import com.getcode.opencode.internal.extensions.toHash
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.FundingSource
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.model.transactions.SwapSuccessCode
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

internal fun Model.IntentId.toId(): ID = value.toByteArray().toList()
internal fun Model.SwapId.toId(): ID = value.toByteArray().toList()
internal fun Model.SwapId.toSwapId(): SwapId = SwapId(value.toByteArray().toList())
internal fun Model.SolanaAccountId.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.Blockhash.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.Blockhash.toHash(): Hash = value.toByteArray().toHash()
internal fun Model.Signature.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun MessagingService.MessageId.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun MessagingService.RendezvousKey.toPublicKey(): PublicKey =
    value.toByteArray().toPublicKey()

internal fun TransactionService.OpenAccountsMetadata.AccountSet.toAccountType() = when (this) {
    TransactionService.OpenAccountsMetadata.AccountSet.USER -> AccountType.Primary
    TransactionService.OpenAccountsMetadata.AccountSet.POOL -> AccountType.Pool
    TransactionService.OpenAccountsMetadata.AccountSet.UNRECOGNIZED -> AccountType.Unknown
}

internal fun TransactionService.ExchangeData.toModel(): ExchangeData.WithRate {
    return ExchangeData.WithRate(
        currencyCode = this.currency,
        exchangeRate = this.exchangeRate,
        nativeAmount = this.nativeAmount,
        quarks = this.quarks,
        mint = this.mint.toPublicKey(),
    )
}

internal fun TransactionService.ExchangeDataWithoutRate.toModel(): ExchangeData.WithoutRate {
    return ExchangeData.WithoutRate(
        currencyCode = this.currency,
        nativeAmount = this.nativeAmount,
    )
}

internal fun MessagingService.RequestToGrabBill.toMessageKind(): MessageKind.RequestToGrabBill {
    return MessageKind.RequestToGrabBill(
        requestor = requestorAccount.toPublicKey()
    )
}

internal fun MessagingService.RequestToGiveBill.toMessageKind(): MessageKind.RequestToGiveBill {
    return MessageKind.RequestToGiveBill(
        mint = mint.toPublicKey()
    )
}

internal fun TransactionService.Metadata.toMetadata(): TransactionMetadata {
    return when (val case = typeCase) {
        TransactionService.Metadata.TypeCase.OPEN_ACCOUNTS -> TransactionMetadata.OpenAccount(
            type = openAccounts.accountSet.toAccountType(),
            mint = openAccounts.mint.toPublicKey(),
        )
        TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> TransactionMetadata.SendPublicPayment(
            source = sendPublicPayment.source.toPublicKey(),
            destination = sendPublicPayment.destination.toPublicKey(),
            destinationOwner = sendPublicPayment.destinationOrNull?.toPublicKey(),
            exchangeData = sendPublicPayment.exchangeData.toModel(),
            isRemoteSend = sendPublicPayment.isRemoteSend,
            isWithdrawal = sendPublicPayment.isWithdrawal,
        )

        TransactionService.Metadata.TypeCase.RECEIVE_PAYMENTS_PUBLICLY -> TransactionMetadata.ReceivePublicPayment(
            source = receivePaymentsPublicly.source.toPublicKey(),
            quarks = receivePaymentsPublicly.quarks,
            isRemoteSend = receivePaymentsPublicly.isRemoteSend,
            exchangeData = receivePaymentsPublicly.exchangeData.toModel(),
            mint = receivePaymentsPublicly.mint.toPublicKey(),
        )

        TransactionService.Metadata.TypeCase.TYPE_NOT_SET -> TransactionMetadata.Unknown
        TransactionService.Metadata.TypeCase.PUBLIC_DISTRIBUTION -> TransactionMetadata.PublicDistribution(
            source = publicDistribution.source.toPublicKey(),
            distributions = publicDistribution.distributionsList.map { distribution ->
                Distribution(
                    destination = distribution.destination.toPublicKey(),
                    amount = Fiat(distribution.quarks)
                )
            },
            mint = publicDistribution.mint.toPublicKey(),
        )
    }
}

internal fun TransactionService.StartSwapRequest.Start.CurrencyCreator.toClientParameters(): VerifiedSwapMetadata.ClientParameters {
    return VerifiedSwapMetadata.ClientParameters(
        id = id.toSwapId(),
        fromMint = fromMint.toPublicKey(),
        toMint = toMint.toPublicKey(),
        amount = amount.toFiat(),
        fundingSource = when (fundingSource) {
            TransactionService.FundingSource.FUNDING_SOURCE_UNKNOWN -> FundingSource.UNKNOWN
            TransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT -> FundingSource.SUBMIT_INTENT
            TransactionService.FundingSource.UNRECOGNIZED -> FundingSource.UNKNOWN
        },
        fundingId = PublicKey.fromBase58(fundingId)
    )
}

internal fun TransactionService.StartSwapResponse.ServerParameters.CurrencyCreator.toServerParameters(): VerifiedSwapMetadata.ServerParameters {
    return VerifiedSwapMetadata.ServerParameters(
        nonce = nonce.toPublicKey(),
        blockhash = blockhash.toHash()
    )
}

internal fun TransactionService.SwapResponse.ServerParameters.CurrencyCreatorStateful.toStatefulProps(): SwapResponseServerParameters.Stateful {
    return SwapResponseServerParameters.Stateful(
        payer = payer.toPublicKey(),
        alts = altsList.map { table ->
            val address = table.address.toPublicKey()
            val entries = table.entriesList.map { it.toPublicKey() }
            AddressLookupTable(address, entries)
        },
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        memoValue = memoValue,
        memoryAccount = memoryAccount.toPublicKey(),
        memoryIndex = memoryIndex,
    )
}

internal fun TransactionService.SwapResponse.ServerParameters.CurrencyCreatorStateless.toStatelessProps(): SwapResponseServerParameters.Stateless {
    return SwapResponseServerParameters.Stateless(
        payer = payer.toPublicKey(),
        recentBlockhash = recentBlockhash.toPublicKey(),
        alts = altsList.map { table ->
            val address = table.address.toPublicKey()
            val entries = table.entriesList.map { it.toPublicKey() }
            AddressLookupTable(address, entries)
        },
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        memoValue = memoValue,
        memoryAccount = memoryAccount.toPublicKey(),
        memoryIndex = memoryIndex,
    )
}

internal fun TransactionService.SwapResponse.Success.toCode(): SwapSuccessCode? {
    return when (this.code) {
        TransactionService.SwapResponse.Success.Code.SWAP_SUBMITTED -> SwapSuccessCode.Submitted
        TransactionService.SwapResponse.Success.Code.SWAP_FINALIZED -> SwapSuccessCode.Finalized
        TransactionService.SwapResponse.Success.Code.UNRECOGNIZED -> null
    }
}

internal fun CurrencyService.VmMetadata.toMetadata(): VmMetadata {
    return VmMetadata(
        vm = vm.toPublicKey(),
        authority = authority.toPublicKey(),
        lockDurationInDays = lockDurationInDays,
    )
}

internal fun CurrencyService.LaunchpadMetadata.toMetadata(): LaunchpadMetadata {
    return LaunchpadMetadata(
        currencyConfig = currencyConfig.toPublicKey(),
        liquidityPool = liquidityPool.toPublicKey(),
        seed = seed.toPublicKey(),
        authority = authority.toPublicKey(),
        mintVault = mintVault.toPublicKey(),
        coreMintVault = coreMintVault.toPublicKey(),
        coreMintFees = coreMintFees.toPublicKey(),
        currentCirculatingSupplyQuarks = supplyFromBonding,
        coreMintLockedQuarks = coreMintLocked,
        sellFeeBps = sellFeeBps
    )
}