package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.account.v1.OcpAccountService
import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.domain.mapping.LiveMintDataMapper
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.IntentMultiTransfer
import com.getcode.opencode.internal.network.extensions.newInstance
import com.getcode.opencode.internal.network.extensions.requestToSubmitActions
import com.getcode.opencode.internal.network.extensions.requestToSubmitSignatures
import com.getcode.opencode.internal.network.extensions.streamLiveMintDataOpenRequest
import com.getcode.opencode.internal.network.extensions.streamLiveMintDataPongRequest
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.parity.renderAccountInfos
import com.getcode.opencode.parity.renderLimits
import com.getcode.opencode.parity.renderLiveMintData
import com.getcode.opencode.parity.renderServerParameters
import com.getcode.opencode.parity.renderSubmitIntentError
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.google.protobuf.Timestamp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what Android sends and decodes for the calls `:services:opencode-client` makes over Wire:
 * StreamLiveMintData (open and pong), GetLimits, GetTokenAccountInfos responses, and the SubmitIntent
 * messages for a SendPublicMultiPayment intent. Its `OcpClientParityTest` asserts the same file on
 * the JVM, Node and the iOS simulator.
 *
 * Requests go through the protobuf-java builders Android uses. Responses are protobuf-java messages
 * built here, serialized, and mapped by Android's own mappers; the fixture keeps the bytes and the
 * rendering from `parityShared`. Nothing leaves the process: GetLimits goes through the real
 * [TransactionApi] into a channel that records the message and fails the call. Keys come from fixed,
 * non-secret seeds, the same as `multi_payment_intent.json`.
 *
 * Run with `-Dfixture.update=true` to rewrite the fixture.
 */
class OcpClientFixtureTest {

    private val ownerSeed = ByteArray(32) { 1 }
    private val owner = Ed25519.createKeyPair(ownerSeed)
    private val mints = listOf(mintOf(0x0F), mintOf(0x11))
    private val nowMillis = 1_767_225_600_123L
    private val consumedSinceMillis = 1_767_139_200_456L
    private val fetchMillis = 1_767_225_601_000L
    private val intentId = key(0x77)

    @Test
    fun androidOcpMessagesMatchFixture() = runBlocking {
        val captured = buildJsonObject {
            put("ownerSeed", ownerSeed.hex())
            put("mintSeeds", mints.joinToString(",") { it.byteArray.first().toString() })
            put("nowMillis", nowMillis)
            put("consumedSinceMillis", consumedSinceMillis)
            put("fetchMillis", fetchMillis)
            put("requests", buildJsonObject { requests() })
            put("responses", buildJsonObject { responses() })
        }

        val file = File(requireNotNull(System.getProperty("ocpClientFixture")) { "ocpClientFixture not set" })
        if (System.getProperty("fixture.update") == "true") {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), captured) + "\n")
        }
        val pinned = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(pinned, captured)
    }

    private suspend fun JsonObjectBuilder.requests() {
        put("streamLiveMintDataOpen", streamLiveMintDataOpenRequest(mints).toByteArray().hex())
        put("streamLiveMintDataPong", streamLiveMintDataPongRequest(nowMillis).toByteArray().hex())

        val channel = CapturingChannel()
        runCatching { TransactionApi(channel).getLimits(owner, consumedSinceMillis) }
        put("getLimits", channel.takeOnly().hex())

        val intent = multiPaymentIntent()
        put("submitActions", intent.requestToSubmitActions(owner).toByteArray().hex())
        intent.apply(serverParameters().serverParametersList.map { ServerParameter.newInstance(it) })
        put("submitSignatures", intent.requestToSubmitSignatures().toByteArray().hex())
    }

    private fun JsonObjectBuilder.responses() {
        val mapper = LiveMintDataMapper()
        liveMintData().forEach { (name, response) ->
            val bytes = response.toByteArray()
            val decoded = OcpCurrencyService.StreamLiveMintDataResponse.parseFrom(bytes)
            val domain = if (decoded.hasData()) mapper.map(decoded.data) else null
            record(name, bytes, renderLiveMintData(domain))
        }

        val limits = limitsResponse()
        val decodedLimits = OcpTransactionService.GetLimitsResponse.parseFrom(limits.toByteArray())
        record(
            "getLimits", limits,
            renderLimits(
                Limits.newInstance(
                    sinceDate = consumedSinceMillis,
                    fetchDate = fetchMillis,
                    sendLimits = decodedLimits.sendLimitsByCurrencyMap,
                    usdTransactedSinceConsumption = decodedLimits.usdTransacted,
                )
            ),
        )

        accountInfos().forEach { (name, response) ->
            val decoded = OcpAccountService.GetTokenAccountInfosResponse.parseFrom(response.toByteArray())
            // AccountService.getTokenAccounts: OK keeps the infos it recognises, keyed by the map key.
            val accounts = when (decoded.result) {
                OcpAccountService.GetTokenAccountInfosResponse.Result.OK ->
                    decoded.tokenAccountInfosMap.mapNotNull { (base58, info) ->
                        AccountInfo.newInstance(info)?.let { PublicKey.fromBase58(base58) to it }
                    }.toMap()
                else -> null
            }
            record(name, response, renderAccountInfos(accounts))
        }

        submitIntentResponses().forEach { (name, response) ->
            val decoded = OcpTransactionService.SubmitIntentResponse.parseFrom(response.toByteArray())
            // IntentExecutor's response handler.
            val rendered = when (decoded.responseCase) {
                OcpTransactionService.SubmitIntentResponse.ResponseCase.SERVER_PARAMETERS ->
                    renderServerParameters(decoded.serverParameters.serverParametersList.map { ServerParameter.newInstance(it) })
                OcpTransactionService.SubmitIntentResponse.ResponseCase.SUCCESS -> "success"
                OcpTransactionService.SubmitIntentResponse.ResponseCase.ERROR -> renderError(SubmitIntentError.typed(decoded.error))
                else -> "empty"
            }
            record(name, response, rendered)
        }
    }

    private fun renderError(error: SubmitIntentError): String {
        // The reasons are private on Android's error classes; their data-class toString carries them.
        val reasons = Regex("""\((?:reasons|details)=\[(.*)]\)$""").find(error.toString())?.groupValues?.get(1)
        val details = reasons?.takeIf { it.isNotEmpty() }?.split(", ").orEmpty()
        return renderSubmitIntentError(error::class.simpleName!!, details)
    }

    private fun JsonObjectBuilder.record(name: String, response: MessageLite, rendered: String) =
        record(name, response.toByteArray(), rendered)

    private fun JsonObjectBuilder.record(name: String, bytes: ByteArray, rendered: String) {
        put(name, buildJsonObject {
            put("bytes", bytes.hex())
            put("domain", rendered)
        })
    }

    // region Inputs

    private fun multiPaymentIntent(): IntentMultiTransfer {
        val authority = PublicKey(owner.publicKeyBytes.toList())
        val cluster = AccountCluster(
            authority = DerivedKey(DerivePath.primary, owner),
            timelock = TimelockDerivedAccounts(
                owner = authority,
                state = ProgramDerivedAccount(key(0), 0),
                vault = ProgramDerivedAccount(key(16), 0),
            ),
        )
        val rate = Rate(fx = 1.37, currency = CurrencyCode.CAD)
        fun share(destination: Int, owner: Int, quarks: Long, native: Long) = IntentMultiTransfer.Share(
            destination = key(destination),
            destinationOwner = key(owner),
            amount = LocalFiat(
                underlyingTokenAmount = Fiat(quarks = quarks, currencyCode = CurrencyCode.USD),
                nativeAmount = Fiat(quarks = native, currencyCode = CurrencyCode.CAD),
                rate = rate,
                mint = mints[0],
            ),
        )
        return IntentMultiTransfer.create(
            shares = listOf(share(33, 49, 800_000, 1_096_000), share(34, 50, 200_000, 274_000)),
            mint = mints[0],
            sourceCluster = cluster,
            verifiedState = VerifiedState(rateProto = verifiedRate("cad", 1.37, 0xA1), reserveProto = verifiedReserve()),
            intentId = intentId,
        )
    }

    private fun verifiedRate(code: String, fx: Double, signatureSeed: Int) =
        OcpCurrencyService.VerifiedCoreMintFiatExchangeRate.newBuilder()
            .setExchangeRate(
                OcpCurrencyService.CoreMintFiatExchangeRate.newBuilder()
                    .setCurrencyCode(code)
                    .setExchangeRate(fx)
                    .setTimestamp(seconds(1_767_225_000)),
            )
            .setSignature(signature(signatureSeed))
            .build()

    private fun verifiedReserve() =
        OcpCurrencyService.VerifiedLaunchpadCurrencyReserveState.newBuilder()
            .setReserveState(
                OcpCurrencyService.LaunchpadCurrencyReserveState.newBuilder()
                    .setMint(accountId(mints[1].byteArray))
                    .setSupplyFromBonding(123_456_789)
                    .setTimestamp(seconds(1_767_225_010)),
            )
            .setSignature(signature(0xB2))
            .build()

    private fun liveMintData() = listOf(
        "liveMintDataRates" to OcpCurrencyService.StreamLiveMintDataResponse.newBuilder()
            .setData(
                OcpCurrencyService.StreamLiveMintDataResponse.LiveData.newBuilder()
                    .setCoreMintFiatExchangeRates(
                        OcpCurrencyService.VerifiedCoreMintFiatExchangeRateBatch.newBuilder()
                            .addExchangeRates(verifiedRate("usd", 1.0, 0xA0))
                            .addExchangeRates(verifiedRate("cad", 1.37, 0xA1))
                            // Not a currency Android knows: dropped.
                            .addExchangeRates(verifiedRate("zzz", 9.5, 0xA2)),
                    ),
            ).build(),
        "liveMintDataReserves" to OcpCurrencyService.StreamLiveMintDataResponse.newBuilder()
            .setData(
                OcpCurrencyService.StreamLiveMintDataResponse.LiveData.newBuilder()
                    .setLaunchpadCurrencyReserveStates(
                        OcpCurrencyService.VerifiedLaunchapdCurrencyReserveStateBatch.newBuilder()
                            .addReserveStates(verifiedReserve()),
                    ),
            ).build(),
        "liveMintDataPing" to OcpCurrencyService.StreamLiveMintDataResponse.newBuilder()
            .setPing(
                Model.ServerPing.newBuilder()
                    .setTimestamp(seconds(1_767_225_600))
                    .setPingDelay(com.google.protobuf.Duration.newBuilder().setSeconds(5)),
            ).build(),
    )

    private fun limitsResponse() = OcpTransactionService.GetLimitsResponse.newBuilder()
        .setResult(OcpTransactionService.GetLimitsResponse.Result.OK)
        .putSendLimitsByCurrency("usd", sendLimit(250f, 500f, 1_000f))
        .putSendLimitsByCurrency("cad", sendLimit(340.5f, 685f, 1_370f))
        .putSendLimitsByCurrency("zzz", sendLimit(1f, 1f, 1f))
        .setUsdTransacted(12.5)
        .build()

    private fun sendLimit(next: Float, perTransaction: Float, perDay: Float) =
        OcpTransactionService.SendLimit.newBuilder()
            .setNextTransaction(next)
            .setMaxPerTransaction(perTransaction)
            .setMaxPerDay(perDay)
            .build()

    private fun accountInfos(): List<Pair<String, OcpAccountService.GetTokenAccountInfosResponse>> {
        fun info(seed: Int, type: Model.AccountType, index: Long) = OcpAccountService.TokenAccountInfo.newBuilder()
            .setAddress(accountId(key(seed).byteArray))
            .setOwner(accountId(owner.publicKeyBytes))
            .setAuthority(accountId(key(seed + 1).byteArray))
            .setAccountType(type)
            .setIndex(index)
            .setBalanceSource(OcpAccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_BLOCKCHAIN)
            .setBalance(1_234_567)
            .setManagementState(OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED)
            .setBlockchainState(OcpAccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_EXISTS)
            .setClaimState(OcpAccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_NOT_CLAIMED)
            .setOriginalExchangeData(
                OcpTransactionService.ExchangeData.newBuilder()
                    .setCurrency("cad")
                    .setExchangeRate(1.37)
                    .setNativeAmount(1.69)
                    .setQuarks(1_234_567)
                    .setMint(accountId(mints[0].byteArray)),
            )
            .setMint(accountId(mints[0].byteArray))
            .setCreatedAt(seconds(1_767_000_000))
            .setIsGiftCardIssuer(type == Model.AccountType.REMOTE_SEND_GIFT_CARD)
            .setUsdCostBasis(0.9)
            .build()

        val primary = info(0x51, Model.AccountType.PRIMARY, 0)
        val giftCard = info(0x53, Model.AccountType.REMOTE_SEND_GIFT_CARD, 7)
        val unknown = info(0x55, Model.AccountType.UNKNOWN, 1)
        return listOf(
            "getTokenAccountInfos" to OcpAccountService.GetTokenAccountInfosResponse.newBuilder()
                .setResult(OcpAccountService.GetTokenAccountInfosResponse.Result.OK)
                .putTokenAccountInfos(key(0x51).base58(), primary)
                .putTokenAccountInfos(key(0x53).base58(), giftCard)
                // An account type Android does not map: dropped.
                .putTokenAccountInfos(key(0x55).base58(), unknown)
                .build(),
            "getTokenAccountInfosNotFound" to OcpAccountService.GetTokenAccountInfosResponse.newBuilder()
                .setResult(OcpAccountService.GetTokenAccountInfosResponse.Result.NOT_FOUND)
                .build(),
        )
    }

    private fun serverParameters() = OcpTransactionService.SubmitIntentResponse.ServerParameters.newBuilder()
        .addServerParameters(nonceParameter(0, nonce = 0x61, blockhash = 0x62))
        .addServerParameters(nonceParameter(1, nonce = 0x63, blockhash = 0x64))
        .build()

    private fun nonceParameter(actionId: Int, nonce: Int, blockhash: Int) =
        OcpTransactionService.ServerParameter.newBuilder()
            .setActionId(actionId)
            .addNonces(
                OcpTransactionService.NoncedTransactionMetadata.newBuilder()
                    .setNonce(accountId(key(nonce).byteArray))
                    .setBlockhash(Model.Blockhash.newBuilder().setValue(ByteString.copyFrom(key(blockhash).byteArray))),
            )
            .setNoPrivacyTransfer(OcpTransactionService.NoPrivacyTransferServerParameter.getDefaultInstance())
            .build()

    private fun submitIntentResponses(): List<Pair<String, OcpTransactionService.SubmitIntentResponse>> {
        fun error(code: OcpTransactionService.SubmitIntentResponse.Error.Code, vararg details: OcpTransactionService.ErrorDetails) =
            OcpTransactionService.SubmitIntentResponse.newBuilder()
                .setError(
                    OcpTransactionService.SubmitIntentResponse.Error.newBuilder()
                        .setCode(code)
                        .addAllErrorDetails(details.toList()),
                ).build()

        fun reason(text: String) = OcpTransactionService.ErrorDetails.newBuilder()
            .setReasonString(OcpTransactionService.ReasonStringErrorDetails.newBuilder().setReason(text))
            .build()

        fun denied(text: String) = OcpTransactionService.ErrorDetails.newBuilder()
            .setDenied(OcpTransactionService.DeniedErrorDetails.newBuilder().setReason(text))
            .build()

        fun invalidSignature(actionId: Int) = OcpTransactionService.ErrorDetails.newBuilder()
            .setInvalidSignature(OcpTransactionService.InvalidSignatureErrorDetails.newBuilder().setActionId(actionId))
            .build()

        val feePayment = OcpTransactionService.ServerParameter.newBuilder()
            .setActionId(2)
            .setFeePayment(
                OcpTransactionService.FeePaymentServerParameter.newBuilder()
                    .setDestination(accountId(key(0x70).byteArray)),
            )
            .build()

        val code = OcpTransactionService.SubmitIntentResponse.Error.Code::class.java.enumConstants
            .associateBy { it.name }
        return listOf(
            "submitIntentServerParameters" to OcpTransactionService.SubmitIntentResponse.newBuilder()
                .setServerParameters(serverParameters().toBuilder().addServerParameters(feePayment))
                .build(),
            "submitIntentSuccess" to OcpTransactionService.SubmitIntentResponse.newBuilder()
                .setSuccess(OcpTransactionService.SubmitIntentResponse.Success.getDefaultInstance())
                .build(),
            "submitIntentDenied" to error(code.getValue("DENIED"), denied("unexpected owner account"), reason("ignored")),
            "submitIntentInvalid" to error(code.getValue("INVALID_INTENT"), reason("payment is a no-op"), reason(""), denied("ignored")),
            "submitIntentSignature" to error(code.getValue("SIGNATURE_ERROR"), reason("bad signature"), invalidSignature(1)),
            "submitIntentStale" to error(code.getValue("STALE_STATE"), reason("race detected: nonce")),
        )
    }

    // endregion
}

private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })
private fun mintOf(seed: Int) = Mint(List(32) { seed.toByte() })
private fun seconds(value: Long): Timestamp = Timestamp.newBuilder().setSeconds(value).build()
private fun accountId(bytes: ByteArray) = Model.SolanaAccountId.newBuilder().setValue(ByteString.copyFrom(bytes)).build()
private fun signature(seed: Int) = Model.Signature.newBuilder().setValue(ByteString.copyFrom(ByteArray(64) { seed.toByte() })).build()
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
