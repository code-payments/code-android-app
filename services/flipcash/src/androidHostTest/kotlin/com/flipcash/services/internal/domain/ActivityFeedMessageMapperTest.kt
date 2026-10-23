package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.directlySentCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.notification
import com.codeinc.flipcash.gen.activity.v1.notificationId
import com.codeinc.flipcash.gen.activity.v1.receivedCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.indirectlySentCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.withdrewCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.depositedCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.boughtCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.soldCryptoNotificationMetadata
import com.codeinc.flipcash.gen.activity.v1.swappedCryptoNotificationMetadata
import com.codeinc.flipcash.gen.common.v1.cryptoPaymentAmount
import com.codeinc.flipcash.gen.common.v1.fiatPaymentAmount
import com.codeinc.flipcash.gen.common.v1.publicKey
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.flipcash.services.models.SwapState
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityFeedMessageMapperTest {

    private val mapper = ActivityFeedMessageMapper()

    private val testIdBytes = ByteString.copyFrom(ByteArray(16) { it.toByte() })
    private val testTimestamp = Timestamp.newBuilder().setSeconds(1700000000).build()

    private fun baseNotification(
        block: com.codeinc.flipcash.gen.activity.v1.NotificationKt.Dsl.() -> Unit = {}
    ) = notification {
        id = notificationId { value = testIdBytes }
        localizedText = "Test notification"
        ts = testTimestamp
        state = Model.NotificationState.NOTIFICATION_STATE_COMPLETED
        block()
    }

    // --- Payment amount mapping ---

    @Test
    fun `no payment amount results in null amount`() {
        val proto = baseNotification()
        val result = mapper.map(proto)

        assertEquals("Test notification", result.text)
        assertNull(result.amount)
    }

    @Test
    fun `payment amount without mint produces LocalFiat via usdf path`() {
        val proto = baseNotification {
            paymentAmount = cryptoPaymentAmount {
                currency = "USD"
                nativeAmount = 5.0
                quarks = 5_000_000L
            }
        }

        val result = mapper.map(proto)
        val amount = result.amount

        assertNotNull(amount)
        assertEquals(5_000_000L, amount.underlyingTokenAmount.quarks)
        assertEquals(5.0, amount.nativeAmount.decimalValue, 0.01)
    }

    @Test
    fun `payment amount with mint produces LocalFiat with rate`() {
        val proto = baseNotification {
            paymentAmount = cryptoPaymentAmount {
                currency = "USD"
                nativeAmount = 10.0
                quarks = 100_000_000L
                mint = publicKey {
                    value = ByteString.copyFrom(ByteArray(32) { 1 })
                }
            }
        }

        val result = mapper.map(proto)
        val amount = result.amount

        assertNotNull(amount)
        assertEquals(100_000_000L, amount.underlyingTokenAmount.quarks)
        assertEquals(10.0, amount.nativeAmount.decimalValue, 0.01)
    }

    // --- Timestamp mapping ---

    @Test
    fun `timestamp is mapped from proto seconds`() {
        val proto = baseNotification()
        val result = mapper.map(proto)

        assertEquals(1700000000L, result.timestamp.epochSeconds)
    }

    // --- State mapping ---

    @Test
    fun `PENDING state mapped correctly`() {
        val proto = baseNotification {
            state = Model.NotificationState.NOTIFICATION_STATE_PENDING
        }
        assertEquals(NotificationState.PENDING, mapper.map(proto).state)
    }

    @Test
    fun `COMPLETED state mapped correctly`() {
        val proto = baseNotification {
            state = Model.NotificationState.NOTIFICATION_STATE_COMPLETED
        }
        assertEquals(NotificationState.COMPLETED, mapper.map(proto).state)
    }

    @Test
    fun `UNKNOWN state mapped correctly`() {
        val proto = baseNotification {
            state = Model.NotificationState.NOTIFICATION_STATE_UNKNOWN
        }
        assertEquals(NotificationState.UNKNOWN, mapper.map(proto).state)
    }

    // --- Metadata mapping ---

    @Test
    fun `directly sent crypto metadata`() {
        val proto = baseNotification {
            directlySentCrypto = directlySentCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.DirectlySentCrypto(), mapper.map(proto).metadata)
    }

    @Test
    fun `received crypto metadata`() {
        val proto = baseNotification {
            receivedCrypto = receivedCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.ReceivedCrypto(), mapper.map(proto).metadata)
    }

    @Test
    fun `withdrew crypto metadata`() {
        val proto = baseNotification {
            withdrewCrypto = withdrewCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.WithdrewCrypto(), mapper.map(proto).metadata)
    }

    @Test
    fun `deposited crypto metadata`() {
        val proto = baseNotification {
            depositedCrypto = depositedCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.DepositedCrypto, mapper.map(proto).metadata)
    }

    @Test
    fun `bought crypto metadata`() {
        val proto = baseNotification {
            boughtCrypto = boughtCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.BoughtToken, mapper.map(proto).metadata)
    }

    @Test
    fun `sold crypto metadata`() {
        val proto = baseNotification {
            soldCrypto = soldCryptoNotificationMetadata {}
        }
        assertEquals(NotificationMetadata.SoldToken, mapper.map(proto).metadata)
    }

    @Test
    fun `swapped crypto executed carries the received amount`() {
        val proto = baseNotification {
            swappedCrypto = swappedCryptoNotificationMetadata {
                from = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 5.0
                    quarks = 5_000_000L
                }
                toAmount = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 4.9
                    quarks = 4_900_000L
                }
                fee = fiatPaymentAmount {
                    currency = "USD"
                    nativeAmount = 0.1
                }
                swapState = Model.SwapState.SWAP_STATE_SUCCEEDED
            }
        }

        val meta = mapper.map(proto).metadata
        assertIs<NotificationMetadata.SwappedCrypto>(meta)
        assertEquals(SwapState.SUCCEEDED, meta.swap.swapState)
        assertEquals(5_000_000L, meta.swap.from.underlyingTokenAmount.quarks)
        assertNotNull(meta.swap.toAmount)
        assertEquals(4_900_000L, meta.swap.toAmount.underlyingTokenAmount.quarks)
    }

    @Test
    fun `swapped crypto pending carries only the destination mint`() {
        val proto = baseNotification {
            swappedCrypto = swappedCryptoNotificationMetadata {
                from = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 5.0
                    quarks = 5_000_000L
                }
                toMint = publicKey { value = ByteString.copyFrom(ByteArray(32) { it.toByte() }) }
                fee = fiatPaymentAmount {
                    currency = "USD"
                    nativeAmount = 0.1
                }
                swapState = Model.SwapState.SWAP_STATE_PENDING
            }
        }

        val meta = mapper.map(proto).metadata
        assertIs<NotificationMetadata.SwappedCrypto>(meta)
        assertEquals(SwapState.PENDING, meta.swap.swapState)
        assertNull(meta.swap.toAmount)
    }

    @Test
    fun `withdrew crypto carries swap metadata when present`() {
        val proto = baseNotification {
            withdrewCrypto = withdrewCryptoNotificationMetadata {
                swapMetadata = swappedCryptoNotificationMetadata {
                    from = cryptoPaymentAmount {
                        currency = "USD"
                        nativeAmount = 5.0
                        quarks = 5_000_000L
                    }
                    toAmount = cryptoPaymentAmount {
                        currency = "USD"
                        nativeAmount = 4.9
                        quarks = 4_900_000L
                    }
                    fee = fiatPaymentAmount {
                        currency = "USD"
                        nativeAmount = 0.1
                    }
                    swapState = Model.SwapState.SWAP_STATE_SUCCEEDED
                }
            }
        }

        val meta = mapper.map(proto).metadata
        assertIs<NotificationMetadata.WithdrewCrypto>(meta)
        assertNotNull(meta.swapMetadata)
        assertEquals(SwapState.SUCCEEDED, meta.swapMetadata.swapState)
    }

    @Test
    fun `indirectly sent crypto metadata has creator and canCancel`() {
        val vaultBytes = ByteArray(32) { 42 }
        val proto = baseNotification {
            indirectlySentCrypto = indirectlySentCryptoNotificationMetadata {
                vault = publicKey { value = ByteString.copyFrom(vaultBytes) }
                canInitiateCancelAction = true
            }
        }

        val metadata = mapper.map(proto).metadata
        assertIs<NotificationMetadata.IndirectlySentCrypto>(metadata)
        assertTrue(metadata.canCancel)
        assertEquals(vaultBytes.toList(), metadata.creator.bytes)
    }

    @Test
    fun `no metadata set maps to Unknown`() {
        val proto = baseNotification()
        assertEquals(NotificationMetadata.Unknown, mapper.map(proto).metadata)
    }

    // --- Currency code mapping ---

    @Test
    fun `invalid currency code falls back to USD`() {
        val proto = baseNotification {
            paymentAmount = cryptoPaymentAmount {
                currency = "INVALID"
                nativeAmount = 1.0
                quarks = 1_000_000L
            }
        }

        val result = mapper.map(proto)
        assertNotNull(result.amount)
    }

    // --- Swap amounts (see model.proto: multi-mint operations carry amounts in additional_metadata) ---

    @Test
    fun `an executed swap reports the source side as its amount`() {
        val sourceMintBytes = ByteArray(32) { 7 }
        val proto = baseNotification {
            swappedCrypto = swappedCryptoNotificationMetadata {
                from = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 5.0
                    quarks = 5_000_000L
                    mint = publicKey { value = ByteString.copyFrom(sourceMintBytes) }
                }
                toAmount = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 4.9
                    quarks = 4_900_000L
                    mint = publicKey { value = ByteString.copyFrom(ByteArray(32) { 9 }) }
                }
                fee = fiatPaymentAmount {
                    currency = "USD"
                    nativeAmount = 0.1
                }
                swapState = Model.SwapState.SWAP_STATE_SUCCEEDED
            }
        }

        val amount = mapper.map(proto).amount

        assertNotNull(amount)
        assertEquals(5_000_000L, amount.underlyingTokenAmount.quarks)
        assertEquals(5.0, amount.nativeAmount.decimalValue, 0.01)
        assertEquals(Mint(sourceMintBytes.toList()).base58(), amount.mint.base58())
    }

    @Test
    fun `a pending swap reports the source side as its amount`() {
        val sourceMintBytes = ByteArray(32) { 7 }
        val proto = baseNotification {
            swappedCrypto = swappedCryptoNotificationMetadata {
                from = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 5.0
                    quarks = 5_000_000L
                    mint = publicKey { value = ByteString.copyFrom(sourceMintBytes) }
                }
                toMint = publicKey { value = ByteString.copyFrom(ByteArray(32) { 9 }) }
                fee = fiatPaymentAmount {
                    currency = "USD"
                    nativeAmount = 0.1
                }
                swapState = Model.SwapState.SWAP_STATE_PENDING
            }
        }

        val amount = mapper.map(proto).amount

        assertNotNull(amount)
        assertEquals(5_000_000L, amount.underlyingTokenAmount.quarks)
        assertEquals(Mint(sourceMintBytes.toList()).base58(), amount.mint.base58())
    }

    @Test
    fun `an explicit payment amount still wins over the swap metadata`() {
        val proto = baseNotification {
            paymentAmount = cryptoPaymentAmount {
                currency = "USD"
                nativeAmount = 12.0
                quarks = 12_000_000L
            }
            swappedCrypto = swappedCryptoNotificationMetadata {
                from = cryptoPaymentAmount {
                    currency = "USD"
                    nativeAmount = 5.0
                    quarks = 5_000_000L
                }
                toMint = publicKey { value = ByteString.copyFrom(ByteArray(32) { 9 }) }
                fee = fiatPaymentAmount {
                    currency = "USD"
                    nativeAmount = 0.1
                }
                swapState = Model.SwapState.SWAP_STATE_PENDING
            }
        }

        val amount = mapper.map(proto).amount

        assertNotNull(amount)
        assertEquals(12_000_000L, amount.underlyingTokenAmount.quarks)
    }
}
