package com.getcode.opencode.client

import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataResponse
import com.codeinc.opencode.wire.transaction.v1.GetLimitsResponse
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.api.intents.IntentMultiTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.parity.parityIntents
import com.getcode.opencode.parity.parityServerParameters
import com.getcode.opencode.parity.renderAccountInfos
import com.getcode.opencode.parity.renderLimits
import com.getcode.opencode.parity.renderLiveMintData
import com.getcode.opencode.parity.renderServerParameters
import com.getcode.opencode.parity.renderSubmitIntentError
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The Wire encoding must send and decode what Android does. `ocp_client_parity.json` is captured
 * from `:services:opencode`'s protobuf-java builders and mappers by its `OcpClientFixtureTest`; this
 * runs on the JVM, Node and the iOS simulator against the same file. Both sides render domain values
 * with `parityShared`'s functions.
 */
class OcpClientParityTest {

    private val fixture = Json.parseToJsonElement(readTestResource("ocp_client_parity.json")).jsonObject
    private val requests = fixture.section("requests")
    private val responses = fixture.section("responses")
    private val owner = Ed25519.createKeyPair(fixture.hex("ownerSeed"))
    private val mints = fixture.text("mintSeeds").split(",").map { mintOf(it.toInt()) }

    @Test
    fun streamLiveMintDataRequestsMatchAndroid() {
        assertEquals(requests.text("streamLiveMintDataOpen"), LiveMintDataRequests.open(mints).encode().hex())
        assertEquals(
            requests.text("streamLiveMintDataPong"),
            LiveMintDataRequests.pong(fixture.long("nowMillis")).encode().hex(),
        )
    }

    @Test
    fun streamLiveMintDataResponsesDecodeLikeAndroid() {
        for (name in listOf("liveMintDataRates", "liveMintDataReserves", "liveMintDataPing")) {
            val recorded = responses.section(name)
            assertEquals(recorded.text("domain"), renderLiveMintData(LiveMintDataRequests.decode(recorded.hex("bytes"))), name)
        }
    }

    @Test
    fun getLimitsMatchesAndroid() {
        val since = fixture.long("consumedSinceMillis")
        assertEquals(requests.text("getLimits"), LimitsRequests.request(owner, since).encode().hex())

        val recorded = responses.section("getLimits")
        val limits = LimitsRequests.limits(
            GetLimitsResponse.ADAPTER.decode(recorded.hex("bytes")),
            sinceMillis = since,
            fetchMillis = fixture.long("fetchMillis"),
        )
        assertEquals(recorded.text("domain"), renderLimits(limits))
    }

    @Test
    fun getTokenAccountInfosDecodesLikeAndroid() {
        for (name in listOf("getTokenAccountInfos", "getTokenAccountInfosNotFound")) {
            val recorded = responses.section(name)
            val accounts = when (val result = AccountInfoResponses.decode(recorded.hex("bytes"))) {
                is AccountInfosResult.Found -> result.accounts
                AccountInfosResult.NotFound -> null
            }
            assertEquals(recorded.text("domain"), renderAccountInfos(accounts), name)
        }
    }

    @Test
    fun submitIntentRequestsMatchAndroid() {
        val intent = multiPaymentIntent()
        assertEquals(requests.text("submitActions"), SubmitIntentCodec.submitActions(intent, owner).hex())

        // Android applies the two nonce parameters; the fee parameter in the recorded response is for
        // an action this intent does not have.
        val parameters = assertIs<SubmitIntentResult.ServerParameters>(
            SubmitIntentCodec.decodeResponse(responses.section("submitIntentServerParameters").hex("bytes")),
        ).parameters
        intent.apply(parameters.take(2))
        assertEquals(requests.text("submitSignatures"), SubmitIntentCodec.submitSignatures(intent).hex())
    }

    @Test
    fun everyIntentBranchEncodesLikeAndroid() {
        val intents = parityIntents(owner, mints[0], verifiedState())
        assertEquals(6, intents.size)
        for ((name, intent) in intents) {
            assertEquals(requests.text("${name}SubmitActions"), SubmitIntentCodec.submitActions(intent, owner).hex(), name)
            intent.apply(parityServerParameters(intent))
            assertEquals(requests.text("${name}SubmitSignatures"), SubmitIntentCodec.submitSignatures(intent).hex(), name)
        }
    }

    @Test
    fun submitIntentResponsesDecodeLikeAndroid() {
        val names = listOf(
            "submitIntentServerParameters", "submitIntentSuccess", "submitIntentDenied",
            "submitIntentInvalid", "submitIntentSignature", "submitIntentStale",
        )
        for (name in names) {
            val recorded = responses.section(name)
            val rendered = when (val result = SubmitIntentCodec.decodeResponse(recorded.hex("bytes"))) {
                is SubmitIntentResult.ServerParameters -> renderServerParameters(result.parameters)
                SubmitIntentResult.Success -> "success"
                is SubmitIntentResult.Error -> renderSubmitIntentError(result.code.name, result.details)
                SubmitIntentResult.Empty -> "empty"
            }
            assertEquals(recorded.text("domain"), rendered, name)
        }
    }

    /**
     * The intent `OcpClientFixtureTest` builds, but with the verified rate and reserve state taken
     * from the recorded StreamLiveMintData responses, as the app re-embeds what the server signed.
     */
    private fun verifiedState(): WireVerifiedState {
        val rates = StreamLiveMintDataResponse.ADAPTER.decode(responses.section("liveMintDataRates").hex("bytes"))
        val reserves = StreamLiveMintDataResponse.ADAPTER.decode(responses.section("liveMintDataReserves").hex("bytes"))
        val cad = rates.data_!!.core_mint_fiat_exchange_rates!!.exchange_rates.single { it.exchange_rate?.currency_code == "cad" }
        val reserve = reserves.data_!!.launchpad_currency_reserve_states!!.reserve_states.single()
        return WireVerifiedState(cad, reserve)
    }

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
            verifiedState = verifiedState(),
            intentId = key(0x77),
        )
    }
}

private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })
private fun mintOf(seed: Int) = Mint(List(32) { seed.toByte() })

private fun JsonObject.section(name: String): JsonObject = getValue(name).jsonObject
private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun JsonObject.long(name: String): Long = getValue(name).jsonPrimitive.long
private fun JsonObject.hex(name: String): ByteArray =
    text(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
