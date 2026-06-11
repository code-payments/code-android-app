package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.codeinc.opencode.gen.transaction.v1.clientExchangeDataOrNull
import com.codeinc.opencode.gen.transaction.v1.destinationOrNull
import com.codeinc.opencode.gen.transaction.v1.launchpadCurrencyReserveStateOrNull
import com.getcode.opencode.internal.extensions.toHash
import com.getcode.opencode.internal.extensions.toMint
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.extensions.toSignature
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.opencode.model.transactions.StatelessSwapSuccessCode
import com.getcode.opencode.model.transactions.SwapSuccessCode
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

internal fun Model.IntentId.toId(): ID = value.toByteArray().toList()
internal fun Model.SwapId.toId(): ID = value.toByteArray().toList()
internal fun Model.SwapId.toSwapId(): SwapId = SwapId(value.toByteArray().toList())
internal fun Model.SolanaAccountId.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.SolanaAccountId.toMint(): Mint = value.toByteArray().toMint()
internal fun Model.Blockhash.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.Blockhash.toHash(): Hash = value.toByteArray().toHash()
internal fun Model.Signature.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.Signature.toSignature(): Signature = value.toByteArray().toSignature()
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
        mint = this.mint.toMint(),
    )
}

internal fun TransactionService.ExchangeDataWithoutRate.toModel(): ExchangeData.WithoutRate {
    return ExchangeData.WithoutRate(
        currencyCode = this.currency,
        nativeAmount = this.nativeAmount,
    )
}

internal fun TransactionService.VerifiedExchangeData.toModel(): ExchangeData.Verified {
    return ExchangeData.Verified(
        mint = this.mint.toMint(),
        quarks = this.quarks,
        nativeAmount = this.nativeAmount,
        verifiedState = VerifiedState(
            rateProto = this.coreMintFiatExchangeRate,
            reserveProto = this.launchpadCurrencyReserveStateOrNull,
        )
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
            mint = openAccounts.mint.toMint(),
        )
        TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> TransactionMetadata.SendPublicPayment(
            source = sendPublicPayment.source.toPublicKey(),
            destination = sendPublicPayment.destination.toPublicKey(),
            destinationOwner = sendPublicPayment.destinationOrNull?.toPublicKey(),
            exchangeData = sendPublicPayment.serverExchangeData.toModel(),
            verifiedExchangeData = sendPublicPayment.clientExchangeDataOrNull?.toModel(),
            isRemoteSend = sendPublicPayment.isRemoteSend,
            isWithdrawal = sendPublicPayment.isWithdrawal,
        )

        TransactionService.Metadata.TypeCase.RECEIVE_PAYMENTS_PUBLICLY -> TransactionMetadata.ReceivePublicPayment(
            source = receivePaymentsPublicly.source.toPublicKey(),
            quarks = receivePaymentsPublicly.quarks,
            isRemoteSend = receivePaymentsPublicly.isRemoteSend,
            exchangeData = receivePaymentsPublicly.exchangeData.toModel(),
            mint = receivePaymentsPublicly.mint.toMint(),
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
            mint = publicDistribution.mint.toMint(),
        )
    }
}

internal fun TransactionService.StatefulSwapResponse.ServerParameters.ReserveExistingCurrencyServerParameters.toProps(): StatefulSwapResponseServerParameters {
    return StatefulSwapResponseServerParameters.ExistingCurrency(
        payer = payer.toPublicKey(),
        nonce = nonce.toPublicKey(),
        blockhash = blockhash.toPublicKey(),
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

internal fun TransactionService.StatefulSwapResponse.ServerParameters.ReserveNewCurrencyServerParameter.toProps(): StatefulSwapResponseServerParameters {
    return StatefulSwapResponseServerParameters.NewCurrency(
        payer = payer.toPublicKey(),
        nonce = nonce.toPublicKey(),
        blockhash = blockhash.toPublicKey(),
        alts = altsList.map { table ->
            val address = table.address.toPublicKey()
            val entries = table.entriesList.map { it.toPublicKey() }
            AddressLookupTable(address, entries)
        },
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        memoValue = memoValue,
        authority = authority.toPublicKey(),
        name = name,
        symbol = symbol,
        seed = seed.toPublicKey(),
        sellFeeBps = sellFeeBps,
        vmLockDurationInDays = vmLockDurationInDays,
        feeDestination = feeDestination.toPublicKey(),
    )
}

internal fun TransactionService.StatefulSwapResponse.ServerParameters.CoinbaseStableSwapperServerParameter.toProps(): StatefulSwapResponseServerParameters {
    return StatefulSwapResponseServerParameters.Stablecoin(
        payer = payer.toPublicKey(),
        nonce = nonce.toPublicKey(),
        blockhash = blockhash.toPublicKey(),
        alts = altsList.map { table ->
            val address = table.address.toPublicKey()
            val entries = table.entriesList.map { it.toPublicKey() }
            AddressLookupTable(address, entries)
        },
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        memoValue = memoValue,
        feeDestination = feeDestination.toPublicKey(),
        poolFeeRecipient = poolFeeRecipient.toPublicKey(),
    )
}

internal fun TransactionService.StatefulSwapRequest.Initiate.ReserveSwapClientParameters.toMetadata(): VerifiedSwapMetadata.Reserve {
    return VerifiedSwapMetadata.Reserve(
        id = id.toSwapId(),
        fromMint = fromMint.toPublicKey(),
        toMint = toMint.toPublicKey(),
        swapAmount = Fiat(quarks = swapAmount),
        feeAmount = Fiat(quarks = feeAmount),
        fundingSource = when (fundingSource) {
            TransactionService.FundingSource.FUNDING_SOURCE_UNKNOWN -> SwapFundingSource.Unknown
            TransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT -> SwapFundingSource.SubmitIntent(PublicKey(fundingId).bytes)
            TransactionService.FundingSource.FUNDING_SOURCE_EXTERNAL_WALLET -> SwapFundingSource.ExternalWallet(
                fundingId.toByteArray().toList())
            TransactionService.FundingSource.FUNDING_SOURCE_COINBASE_ONRAMP -> SwapFundingSource.CoinbaseOnramp(fundingId)
            TransactionService.FundingSource.UNRECOGNIZED -> SwapFundingSource.Unknown
        }
    )
}

internal fun TransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.toMetadata(): VerifiedSwapMetadata.StableCoin {
    return VerifiedSwapMetadata.StableCoin(
        id = id.toSwapId(),
        fromMint = fromMint.toPublicKey(),
        toMint = toMint.toPublicKey(),
        swapAmount = Fiat(quarks = swapAmount),
        feeAmount = Fiat(quarks = feeAmount),
        fundingSource = when (fundingSource) {
            TransactionService.FundingSource.FUNDING_SOURCE_UNKNOWN -> SwapFundingSource.Unknown
            TransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT -> SwapFundingSource.SubmitIntent(PublicKey(fundingId).bytes)
            TransactionService.FundingSource.FUNDING_SOURCE_EXTERNAL_WALLET -> SwapFundingSource.ExternalWallet(
                fundingId.toByteArray().toList())
            TransactionService.FundingSource.FUNDING_SOURCE_COINBASE_ONRAMP -> SwapFundingSource.CoinbaseOnramp(fundingId)
            TransactionService.FundingSource.UNRECOGNIZED -> SwapFundingSource.Unknown
        },
        destinationOwner = destinationOwner.toPublicKey(),
    )
}

internal fun TransactionService.StatefulSwapResponse.Success.toCode(): SwapSuccessCode? {
    return when (this.code) {
        TransactionService.StatefulSwapResponse.Success.Code.OK -> SwapSuccessCode.Ok
        TransactionService.StatefulSwapResponse.Success.Code.UNRECOGNIZED -> null
    }
}

internal fun TransactionService.StatelessSwapResponse.ServerParameters.CoinbaseStableSwapperServerParameter.toProps(): StatelessSwapServerParameters {
    return StatelessSwapServerParameters(
        payer = payer.toPublicKey(),
        blockhash = blockhash.toHash(),
        alts = altsList.map { table ->
            val address = table.address.toPublicKey()
            val entries = table.entriesList.map { it.toPublicKey() }
            AddressLookupTable(address, entries)
        },
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        memoValue = memoValue,
        poolFeeRecipient = poolFeeRecipient.toPublicKey(),
    )
}

internal fun TransactionService.StatelessSwapResponse.Success.toCode(): StatelessSwapSuccessCode? {
    return when (this.code) {
        TransactionService.StatelessSwapResponse.Success.Code.SUBMITTED -> StatelessSwapSuccessCode.Submitted
        TransactionService.StatelessSwapResponse.Success.Code.FINALIZED -> StatelessSwapSuccessCode.Finalized
        TransactionService.StatelessSwapResponse.Success.Code.UNRECOGNIZED -> null
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
        currentCirculatingSupplyQuarks = supplyFromBonding,
        sellFeeBps = sellFeeBps,
        price = Fiat(fiat = price),
        marketCap = Fiat(fiat = marketCap),
    )
}