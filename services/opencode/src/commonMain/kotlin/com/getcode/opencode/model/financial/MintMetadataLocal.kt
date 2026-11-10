package com.getcode.opencode.model.financial

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.libs.parcelable.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.extensions.deriveVmOmnibusAddress
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.libs.parcelable.IgnoredOnParcel
import com.getcode.libs.parcelable.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

data class TokenWithLocalizedBalance(
    val token: Token,
    val balance: LocalFiat,
    val appreciation: LocalFiat = LocalFiat.MIN_VALUE,
    val displayName: String = token.name,
) {
    val isReserves: Boolean
        get() = token.address == Mint.usdf

    val costBasis: LocalFiat
        get() = balance - appreciation
}

/**
 * Builds a local [Token] stub for a currency that was just launched via
 * [com.getcode.opencode.controllers.CurrencyController.launchToken] but whose
 * on-chain [LaunchpadMetadata] has not yet been propagated by the server.
 *
 * The returned stub is only intended to flow into
 * [com.getcode.opencode.controllers.TransactionController.buy] so the atomic
 * new-currency buy swap can execute. Its null [com.codeinc.opencode.gen.currency.v1.launchpadMetadata] — together
 * with an address that isn't [Mint.usdf] — is also the signal inside
 * `TransactionController.buy` to skip the pre-flight `createUserAccount` call
 * (the atomic swap itself creates the VM and the VM deposit ATA).
 *
 * All on-chain values that actually drive transaction bytes (sellFeeBps,
 * vmLockDurationInDays) still come from
 * [com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters.NewCurrency] inside
 * `buildNewCurrencyBuyInstructions` — this stub never drives those.
 */
fun MintMetadata.Companion.fromLaunch(
    mint: Mint,
    request: TokenCreateRequest,
    owner: PublicKey,
    lockDurationInDays: Int = TimelockDerivedAccounts.lockoutInDays.toInt(),
): Token = MintMetadata(
    address = mint,
    decimals = 10,
    name = request.name.text,
    symbol = request.symbol?.text ?: request.name.text.take(6).uppercase(),
    description = request.description?.text.orEmpty(),
    createdAt = Clock.System.now(),
    imageUrl = "",
    vmMetadata = VmMetadata(
        authority = owner,
        vm = PublicKey.deriveVirtualMachineAccount(
            mint = mint,
            authority = owner,
            lockout = lockDurationInDays.toUByte(),
        ).publicKey,
        lockDurationInDays = lockDurationInDays,
    ),
    launchpadMetadata = null,
    billCustomizations = request.bill,
    socialLinks = emptyList(),
    holderMetrics = HolderMetrics.None,
)
