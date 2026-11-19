package com.flipcash.shared.payments.request

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flipcash.app.core.storage.PreferencesFiles
import com.flipcash.services.user.UserManager
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A conversion this device has started, or is about to start, to fund a collection payment: the
 * swap id and funding intent id it was submitted under, kept so a retry asks for the same swap
 * instead of starting a second one.
 */
data class FundingLegRecord(
    val from: Mint,
    val to: Mint,
    val swapId: SwapId,
    val fundingIntentId: List<Byte>,
)

/**
 * A top-up this device ordered to fund a collection payment, kept from before Google Pay opens
 * until its Dollars are seen or the order is known to have failed, so a retry, a relaunch or a
 * second tap waits on it and never orders again (group-collections spec, "Adding money and
 * recovery" item 1).
 *
 * @property orderId the Coinbase order.
 * @property usdCents what the order lands, in US cents.
 * @property baselineDollarsMicros the Dollars balance, in USD micros, just before the order.
 */
data class PendingTopUpRecord(
    val orderId: String,
    val usdCents: Long,
    val baselineDollarsMicros: Long,
)

/**
 * What collection funding keeps across attempts and launches. Legs are keyed by payment request id
 * and live only until their swap's outcome is known; the last paid currency is the default for the
 * next collection payment.
 */
interface CollectionFundingStore {
    suspend fun legs(requestId: ID): List<FundingLegRecord>
    suspend fun put(requestId: ID, leg: FundingLegRecord)
    suspend fun remove(requestId: ID, swapId: SwapId)
    suspend fun clear(requestId: ID)

    /** The top-up ordered for [requestId] whose outcome isn't known yet, or null. [clear] keeps it. */
    suspend fun pendingTopUp(requestId: ID): PendingTopUpRecord?
    suspend fun putPendingTopUp(requestId: ID, record: PendingTopUpRecord)
    suspend fun clearPendingTopUp(requestId: ID)

    /** The currency of the last successful collection payment, or null before the first. */
    fun lastPaid(): Flow<Mint?>
    suspend fun setLastPaid(mint: Mint)
}

/** [CollectionFundingStore] in a preferences file, per account, as the chat's stores are. */
@OptIn(ExperimentalStdlibApi::class)
@Singleton
internal class DataStoreCollectionFundingStore(
    private val dataStore: DataStore<Preferences>,
    private val owners: Flow<String?>,
    private val currentOwner: () -> String?,
) : CollectionFundingStore {

    @Inject
    constructor(preferencesFiles: PreferencesFiles, userManager: UserManager) : this(
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { preferencesFiles.file(DATASTORE_NAME) },
        ),
        owners = userManager.state.map { it.accountId?.hexEncodedString() },
        currentOwner = { userManager.accountId?.hexEncodedString() },
    )

    private val data: Flow<Preferences>
        get() = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    override suspend fun legs(requestId: ID): List<FundingLegRecord> {
        val owner = currentOwner() ?: return emptyList()
        return data.first()[legsKey(owner, requestId)].orEmpty().mapNotNull(::decode)
    }

    override suspend fun put(requestId: ID, leg: FundingLegRecord) {
        val owner = currentOwner() ?: return
        dataStore.edit { prefs ->
            val key = legsKey(owner, requestId)
            prefs[key] = prefs[key].orEmpty() + encode(leg)
        }
    }

    override suspend fun remove(requestId: ID, swapId: SwapId) {
        val owner = currentOwner() ?: return
        dataStore.edit { prefs ->
            val key = legsKey(owner, requestId)
            val kept = prefs[key].orEmpty().filterNot { decode(it)?.swapId == swapId }.toSet()
            if (kept.isEmpty()) prefs.remove(key) else prefs[key] = kept
        }
    }

    override suspend fun clear(requestId: ID) {
        val owner = currentOwner() ?: return
        dataStore.edit { it.remove(legsKey(owner, requestId)) }
    }

    override suspend fun pendingTopUp(requestId: ID): PendingTopUpRecord? {
        val owner = currentOwner() ?: return null
        return data.first()[topUpKey(owner, requestId)]?.let(::decodeTopUp)
    }

    override suspend fun putPendingTopUp(requestId: ID, record: PendingTopUpRecord) {
        val owner = currentOwner() ?: return
        dataStore.edit { it[topUpKey(owner, requestId)] = encodeTopUp(record) }
    }

    override suspend fun clearPendingTopUp(requestId: ID) {
        val owner = currentOwner() ?: return
        dataStore.edit { it.remove(topUpKey(owner, requestId)) }
    }

    override fun lastPaid(): Flow<Mint?> =
        combine(owners.distinctUntilChanged(), data) { owner, prefs ->
            owner?.let { prefs[lastPaidKey(it)] }?.let(::Mint)
        }.distinctUntilChanged()

    override suspend fun setLastPaid(mint: Mint) {
        val owner = currentOwner() ?: return
        dataStore.edit { it[lastPaidKey(owner)] = mint.base58() }
    }

    private fun legsKey(owner: String, requestId: ID) =
        stringSetPreferencesKey("legs-$owner-${requestId.hexEncodedString()}")

    private fun lastPaidKey(owner: String) = stringPreferencesKey("last-paid-$owner")

    private fun topUpKey(owner: String, requestId: ID) =
        stringPreferencesKey("top-up-$owner-${requestId.hexEncodedString()}")

    private fun encodeTopUp(record: PendingTopUpRecord): String =
        listOf(record.orderId, record.usdCents, record.baselineDollarsMicros).joinToString(SEPARATOR)

    private fun decodeTopUp(value: String): PendingTopUpRecord? = runCatching {
        val (orderId, cents, baseline) = value.split(SEPARATOR)
        PendingTopUpRecord(orderId, cents.toLong(), baseline.toLong())
    }.getOrNull()

    private fun encode(leg: FundingLegRecord): String = listOf(
        leg.from.base58(),
        leg.to.base58(),
        leg.swapId.value.hexEncodedString(),
        leg.fundingIntentId.hexEncodedString(),
    ).joinToString(SEPARATOR)

    private fun decode(value: String): FundingLegRecord? = runCatching {
        val (from, to, swapId, funding) = value.split(SEPARATOR)
        FundingLegRecord(
            from = Mint(from),
            to = Mint(to),
            swapId = SwapId(swapId.hexToByteArray().toList()),
            fundingIntentId = funding.hexToByteArray().toList(),
        )
    }.getOrNull()

    private companion object {
        const val DATASTORE_NAME = "collection_funding"
        const val SEPARATOR = "|"
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun List<Byte>.hexEncodedString(): String = toByteArray().toHexString()
