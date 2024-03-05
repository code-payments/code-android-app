package com.getcode.solana.organizer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TrayTest {
    lateinit var context: Context

    private val mnemonic = MnemonicPhrase.newInstance(
        words = "couple divorce usage surprise before range feature source bubble chunk spot away".split(
            " "
        )
    )!!


    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun testSlotsUp() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to Kin.fromKin(1) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to Kin.fromKin(2) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to Kin.fromKin(3) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to Kin.fromKin(5) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(tray.slotUp(SlotType.Bucket1)?.type, SlotType.Bucket10)
        assertEquals(tray.slotUp(SlotType.Bucket10)?.type, SlotType.Bucket100)
        assertEquals(tray.slotUp(SlotType.Bucket100)?.type, SlotType.Bucket1k)
        assertEquals(tray.slotUp(SlotType.Bucket1k)?.type, SlotType.Bucket10k)
        assertEquals(tray.slotUp(SlotType.Bucket10k)?.type, SlotType.Bucket100k)
        assertEquals(tray.slotUp(SlotType.Bucket100k)?.type, SlotType.Bucket1m)
        assertEquals(tray.slotUp(SlotType.Bucket1m)?.type, null)
    }

    @Test
    fun testSlotsDown() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to Kin.fromKin(1) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to Kin.fromKin(2) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to Kin.fromKin(3) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to Kin.fromKin(5) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(tray.slotDown(SlotType.Bucket1)?.type, null)
        assertEquals(tray.slotDown(SlotType.Bucket10)?.type, SlotType.Bucket1)
        assertEquals(tray.slotDown(SlotType.Bucket100)?.type, SlotType.Bucket10)
        assertEquals(tray.slotDown(SlotType.Bucket1k)?.type, SlotType.Bucket100)
        assertEquals(tray.slotDown(SlotType.Bucket10k)?.type, SlotType.Bucket1k)
        assertEquals(tray.slotDown(SlotType.Bucket100k)?.type, SlotType.Bucket10k)
        assertEquals(tray.slotDown(SlotType.Bucket1m)?.type, SlotType.Bucket100k)
    }

    @Test
    fun testSetBalances() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(1) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(2) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(3) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(5) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(tray.slots.size, 7)
        assertEquals(Kin.fromKin(1) * SlotType.Bucket1.getBillValue(), tray.slot(SlotType.Bucket1).partialBalance)
        assertEquals(Kin.fromKin(2) * SlotType.Bucket10.getBillValue(), tray.slot(SlotType.Bucket10).partialBalance)
        assertEquals(Kin.fromKin(3) * SlotType.Bucket100.getBillValue(), tray.slot(SlotType.Bucket100).partialBalance)
        assertEquals(Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(), tray.slot(SlotType.Bucket1k).partialBalance)
        assertEquals(Kin.fromKin(5) * SlotType.Bucket10k.getBillValue(), tray.slot(SlotType.Bucket10k).partialBalance)
        assertEquals(Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(), tray.slot(SlotType.Bucket100k).partialBalance)
        assertEquals(Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(), tray.slot(SlotType.Bucket1m).partialBalance)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket10k) to Kin.fromKin(7) * SlotType.Bucket10k.getBillValue(),
            )
        )

        assertEquals(Kin.fromKin(1) * SlotType.Bucket1.getBillValue(), tray.slot(SlotType.Bucket1).partialBalance)
        assertEquals(Kin.fromKin(2) * SlotType.Bucket10.getBillValue(), tray.slot(SlotType.Bucket10).partialBalance)
        assertEquals(Kin.fromKin(3) * SlotType.Bucket100.getBillValue(), tray.slot(SlotType.Bucket100).partialBalance)
        assertEquals(Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(), tray.slot(SlotType.Bucket1k).partialBalance)
        assertEquals(Kin.fromKin(7) * SlotType.Bucket10k.getBillValue(), tray.slot(SlotType.Bucket10k).partialBalance)
        assertEquals(Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(), tray.slot(SlotType.Bucket100k).partialBalance)
        assertEquals(Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(), tray.slot(SlotType.Bucket1m).partialBalance)
    }

    @Test
    fun testSetPartialBalances() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(9) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(9) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(9) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(9) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(9) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(9) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(9) * SlotType.Bucket1m.getBillValue(),
            )
        )
    }

    @Test
    fun testBalance() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(3) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(0) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(0) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(0) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(0) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(0) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(0) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(3, tray.availableBalance.toKin().toInt())

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(0) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(0) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(0) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(0) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(0) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(0) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(4_000, tray.availableBalance.toKin().toInt())

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(0) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(0) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(0) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(0) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(0) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(0) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(5) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(5_000_000, tray.availableBalance.toKin().toInt())

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(1) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(2) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(3) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(4) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(5) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(6) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(7) * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(7_654_321, tray.availableBalance.toKin().toInt())
    }

    @Test
    fun testExchangeLargeToSmallFull() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(1) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(1) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(1) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(1) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(1) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(1) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(1) * SlotType.Bucket1m.getBillValue(),
            )
        )

        val exchanges = tray.exchangeLargeToSmall()

        assertEquals(6, exchanges.size)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1m),   to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(1_000_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(100_000)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(10_000)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(1_000)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(100)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket1),    kin = Kin.fromKin(10)), exchanges[5])

        assertEquals(11.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(100.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(10_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(100_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testExchangeLargeToSmallLargestBillOnly() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(0) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(0) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(0) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(0) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(0) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(0) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(1) * SlotType.Bucket1m.getBillValue(),
            )
        )

        val exchanges = tray.exchangeLargeToSmall()

        assertEquals(exchanges.size, 6)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1m),   to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(1_000_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(100_000)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(10_000)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(1_000)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(100)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket1),    kin = Kin.fromKin(10)), exchanges[5])

        assertEquals(10.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testExchangeLargeToSmallLargestBillOverflowing() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1m) to Kin.fromKin(20) * SlotType.Bucket1m.getBillValue(),
            )
        )

        val exchanges = tray.exchangeLargeToSmall()

        assertEquals(exchanges.size, 6)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1m),   to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(1_000_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(100_000)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(10_000)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(1_000)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(100)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket1),    kin = Kin.fromKin(10)), exchanges[5])

        assertEquals(10.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(),    0.0)
        assertEquals(90.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(),   0.0)
        assertEquals(900.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(),  0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(),   0.0)
        assertEquals(90_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(),  0.0)
        assertEquals(900_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(19_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(),   0.0)
    }

    @Test
    fun testExchangeSmallToLargeSmallestBillOnly() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to Kin.fromKin(1_000_000),
            )
        )

        val exchanges = tray.exchangeSmallToLarge()

        assertEquals(15, exchanges.size)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(900_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(90_000)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(9_000)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(900)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(90)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(900_000)), exchanges[5])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(90_000)), exchanges[6])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(9_000)), exchanges[7])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(900)), exchanges[8])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(900_000)), exchanges[9])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(90_000)), exchanges[10])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(9_000)), exchanges[11])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(900_000)), exchanges[12])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(90_000)), exchanges[13])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k), to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(900_000)), exchanges[14])

        assertEquals(10.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testExchangeSmallToLarge1000() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1k) to Kin.fromKin(1_000_000),
            )
        )

        val exchanges = tray.exchangeSmallToLarge()

        assertEquals(3, exchanges.size)

        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket1k),
                to = AccountType.Bucket(SlotType.Bucket10k),
                kin = Kin.fromKin(900_000)
            ), exchanges[0]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket1k),
                to = AccountType.Bucket(SlotType.Bucket10k),
                kin = Kin.fromKin(90_000)
            ), exchanges[1]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10k),
                to = AccountType.Bucket(SlotType.Bucket100k),
                kin = Kin.fromKin(900_000)
            ), exchanges[2]
        )

        assertEquals(0.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(10_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(900_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testRedistributeFromMiddle() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket10k) to Kin.fromKin(1_000_000),
            )
        )

        val exchanges = tray.redistribute()

        assertEquals(5, exchanges.size)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(10_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(1_000)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(100)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket1),    kin = Kin.fromKin(10)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(900_000)), exchanges[4])

        assertEquals(10.0, tray.slot(SlotType.Bucket1).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(90.0, tray.slot(SlotType.Bucket10).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(900.0, tray.slot(SlotType.Bucket100).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(90_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(900_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKin().toDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKin().toDouble(), 0.0)
    }

    @Test
    fun testRedistributeFull() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(19) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(28) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(16) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(39) * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(42) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(17) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(1) * SlotType.Bucket1m.getBillValue(),
            )
        )


        val exchanges = tray.redistribute()

        assertEquals(5, exchanges.size)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),    to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(10)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(200)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket10k),  kin = Kin.fromKin(30_000)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(300_000)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket1m),   kin = Kin.fromKin(1_000_000)), exchanges[4])

        assertEquals(9.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(90.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_800.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(150_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(2_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testRedistributeFullWithGap() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(19) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(28) * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(16) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(0) * SlotType.Bucket1k.getBillValue(), //gap
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(42) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(17) * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(1) * SlotType.Bucket1m.getBillValue(),
            )
        )


        val exchanges = tray.redistribute()

        assertEquals(5, exchanges.size)

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),   kin = Kin.fromKin(10_000)), exchanges[0])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),    to = AccountType.Bucket(
            SlotType.Bucket10),   kin = Kin.fromKin(10)), exchanges[1])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket100),  kin = Kin.fromKin(200)), exchanges[2])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket100k), kin = Kin.fromKin(300_000)), exchanges[3])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket1m),   kin = Kin.fromKin(1_000_000)), exchanges[4])

        assertEquals( 9.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 90.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 1_800.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 10_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 110_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 1_000_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals( 2_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testNormalize() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        val cases: List<Triple<SlotType, Int, List<Int>>> =
            listOf(
                Triple(SlotType.Bucket1k, 5000, listOf(5000)),
                Triple(SlotType.Bucket1k, 500, listOf()),
                Triple(SlotType.Bucket1k, 0, listOf()),
                Triple(SlotType.Bucket100, 1200, listOf(900, 300)),
                Triple(SlotType.Bucket100, 1050, listOf(900, 100)),
                Triple(SlotType.Bucket1, 20, listOf(9, 9, 2))
            )

        cases.forEach { triple ->
            val (slotType, kin, expectation) = triple

            val amounts = mutableListOf<Kin>()
            tray.normalize(slotType = slotType, amount = Kin.fromKin(kin)) { iterationAmount ->
                amounts.add(iterationAmount)
            }
            assertEquals(expectation.map { Kin.fromKin(it) }, amounts)
        }
    }

    @Test
    fun testNormalizeLargest() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        val cases: List<Pair<Int, List<Int>>> =
            listOf(
                Pair(
                    1_489_725,
                    listOf(
                        1_000_000,
                        400_000,
                        80_000,
                        9_000,
                        700,
                        20,
                        5,
                    )
                ),
                Pair(
                    10_893_257,
                    listOf(
                        9_000_000,
                        1_000_000,
                        800_000,
                        90_000,
                        3_000,
                        200,
                        50,
                        7,
                    )
                ),
                Pair(
                    500_000,
                    listOf(
                        500_000,
                    )
                ),
                Pair(
                    950_204,
                    listOf(
                        900_000,
                        50_000,
                        200,
                        4,
                    )
                ),
                Pair(
                    30_852, listOf(
                        30_000,
                        800,
                        50,
                        2,
                    )
                ),
            )

        cases.forEach { pair ->
            val (kin, expectation) = pair

            val amounts = mutableListOf<Kin>()
            tray.normalizeLargest(amount = Kin.fromKin(kin)) { iterationAmount ->
                amounts.add(iterationAmount)
            }
            assertEquals(expectation.map { Kin.fromKin(it) }, amounts)
        }
    }

    @Test
    fun testNaiveTransfer() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to    Kin.fromKin(10) * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to   Kin.fromKin(9)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to  Kin.fromKin(19) * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to   Kin.fromKin(8)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to  Kin.fromKin(9)  * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(9)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to   Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        val exchanges = tray.transfer(amount = Kin.fromKin(9_000))

        assertEquals(3, exchanges.size)

        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing, kin = Kin.fromKin(8_000)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Outgoing, kin = Kin.fromKin(900)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])

        assertEquals(10, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(90, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(900, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(90_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(900_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())

        assertEquals(991_000.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, tray.outgoing.partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testNaiveTransferInsufficientBalance() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        assertThrows(Tray.OrganizerException.InsufficientTrayBalanceException::class.java) {
            tray.transfer(amount = Kin.fromKin(900))
        }
    }


    @Test
    fun testDynamicWithdrawalStep1GreaterThan() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to    Kin.fromKin(1)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to   Kin.fromKin(1)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to  Kin.fromKin(1)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to   Kin.fromKin(1)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to  Kin.fromKin(10) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(9)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to   Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        val step = tray.withdrawDynamicallyStep1(amount = Kin.fromKin(111))

        assertEquals(0.0, step.remaining.toKinValueDouble(), 0.0)
        assertEquals(3, step.index)

        val exchanges = step.exchanges

        assertEquals(exchanges.size, 3)
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing, kin = Kin.fromKin(1)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing, kin = Kin.fromKin(10)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])

        assertEquals(0, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(1_000, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(100_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(900_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())
    }

    @Test
    fun testDynamicWithdrawalStep1LessThan() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to    Kin.fromKin(1)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to   Kin.fromKin(1)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to  Kin.fromKin(1)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to   Kin.fromKin(1)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to  Kin.fromKin(10) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(9)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to   Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        val step = tray.withdrawDynamicallyStep1(amount = Kin.fromKin(9_000))

        assertEquals(7_889, step.remaining.toKinTruncatingLong())
        assertEquals(4, step.index)

        val exchanges = step.exchanges

        assertEquals(4, exchanges.size)
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing, kin = Kin.fromKin(1)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing, kin = Kin.fromKin(10)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing, kin = Kin.fromKin(1_000)), exchanges[3])

        assertEquals(0, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(100_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(900_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())
    }

    @Test
    fun testDynamicWithdrawalStep2() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to    Kin.fromKin(1)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to   Kin.fromKin(1)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to  Kin.fromKin(1)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to   Kin.fromKin(1)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to  Kin.fromKin(10) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to Kin.fromKin(9)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to   Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        val step = tray.withdrawDynamicallyStep1(amount = Kin.fromKin(9_000))

        assertEquals(7_889, step.remaining.toKinTruncatingLong())
        assertEquals(1_111, step.exchanges.map { it.kin }.reduce { acc, kin -> acc + kin }.toKinTruncatingLong())
        assertEquals(4, step.index)

        val finalExchanges = tray.withdrawDynamicallyStep2(step = step)
        val exchanges = step.exchanges + finalExchanges

        assertEquals(12, exchanges.size)

        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing, kin = Kin.fromKin(1)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing, kin = Kin.fromKin(10)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing, kin = Kin.fromKin(1_000)), exchanges[3])

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k), to = AccountType.Bucket(
            SlotType.Bucket1k),  kin = Kin.fromKin(10_000)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Bucket(
            SlotType.Bucket100), kin = Kin.fromKin(1_000)), exchanges[5])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing,        kin = Kin.fromKin(7_000)), exchanges[6])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Bucket(
            SlotType.Bucket10),  kin = Kin.fromKin(100)), exchanges[7])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing,        kin = Kin.fromKin(800)), exchanges[8])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket1),   kin = Kin.fromKin(10)), exchanges[9])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing,        kin = Kin.fromKin(80)), exchanges[10])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing,        kin = Kin.fromKin(9)), exchanges[11])

        assertEquals(1, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(10, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(100, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(2_000, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(90_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(900_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())
    }

    @Test
    fun testDynamicWithdrawalStep2InvalidIndex() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        // Too low
        val step1 = InternalDynamicStep(
            remaining = Kin(0),
            index = 0,
            exchanges = listOf()
        )
        val results1 = tray.withdrawDynamicallyStep2(step = step1)
        assertEquals(listOf<InternalExchange>(), results1)

        // Too high
        val step2 = InternalDynamicStep(
            remaining = Kin(0),
            index = 9,
            exchanges = listOf()
        )
        val results2 = tray.withdrawDynamicallyStep2(step = step2)
        assertEquals(listOf<InternalExchange>(), results2)
    }

    @Test
    fun testDynamicWithdrawalStep2NoRemaining() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val step = InternalDynamicStep(
            remaining = Kin.fromKin(0),
            index = 2,
            exchanges = listOf()
        )

        val exchanges = tray.withdrawDynamicallyStep2(step = step)

        assertEquals(listOf<InternalExchange>(), exchanges)
    }

    @Test
    fun testDynamicWithdrawalAndRedistribute() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(1)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(1)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(1)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(1)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(10) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(9)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(1_001_111.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        val exchanges = tray.transfer(amount = Kin.fromKin(9_000))

        assertEquals(12, exchanges.size)

        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing, kin = Kin.fromKin(1)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing, kin = Kin.fromKin(10)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing, kin = Kin.fromKin(1_000)), exchanges[3])

        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k), to = AccountType.Bucket(
            SlotType.Bucket1k),  kin = Kin.fromKin(10_000)), exchanges[4])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Bucket(
            SlotType.Bucket100), kin = Kin.fromKin(1_000)), exchanges[5])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),  to = AccountType.Outgoing,        kin = Kin.fromKin(7_000)), exchanges[6])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Bucket(
            SlotType.Bucket10),  kin = Kin.fromKin(100)), exchanges[7])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing,        kin = Kin.fromKin(800)), exchanges[8])
        assertEquals(
            InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Bucket(
            SlotType.Bucket1),   kin = Kin.fromKin(10)), exchanges[9])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),  to = AccountType.Outgoing,        kin = Kin.fromKin(80)), exchanges[10])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1),   to = AccountType.Outgoing,        kin = Kin.fromKin(9)), exchanges[11])

        assertEquals(1, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(10, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(100, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(2_000, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(90_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(900_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())

        assertEquals(992_111.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000, tray.outgoing.partialBalance.toKinTruncatingLong())

        val redistributions = tray.redistribute()

        assertEquals(5, redistributions.size)

        assertEquals(redistributions[0], InternalExchange(from = AccountType.Bucket(SlotType.Bucket10k),  to = AccountType.Bucket(
            SlotType.Bucket1k),  kin = Kin.fromKin(10_000))
        )
        assertEquals(redistributions[1], InternalExchange(from = AccountType.Bucket(SlotType.Bucket100k), to = AccountType.Bucket(
            SlotType.Bucket10k), kin = Kin.fromKin(100_000))
        )
        assertEquals(redistributions[2], InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Bucket(
            SlotType.Bucket100), kin = Kin.fromKin(1_000))
        )
        assertEquals(redistributions[3], InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Bucket(
            SlotType.Bucket10),  kin = Kin.fromKin(100))
        )
        assertEquals(redistributions[4], InternalExchange(from = AccountType.Bucket(SlotType.Bucket10),   to = AccountType.Bucket(
            SlotType.Bucket1),   kin = Kin.fromKin(10))
        )

        assertEquals(11, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(100, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(1_000, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(11_000, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(180_000, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(800_000, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())

        assertEquals(992_111, tray.availableBalance.toKinTruncatingLong())
        assertEquals(9_000, tray.outgoing.partialBalance.toKinTruncatingLong())
    }

    @Test
    fun testDynamicWithdrawalExample1() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(13)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(15)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(10)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(5)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(0) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(0)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(0)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(6_163.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        val exchanges = tray.transfer(amount = Kin.fromKin(6_000)) // Should use naive strategy (no exchanges below)

        assertEquals(3, exchanges.size)

        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket1k),   to = AccountType.Outgoing, kin = Kin.fromKin(5_000)), exchanges[0])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100),  to = AccountType.Outgoing, kin = Kin.fromKin(900)), exchanges[1])
        assertEquals(InternalExchange(from = AccountType.Bucket(SlotType.Bucket100), to = AccountType.Outgoing, kin = Kin.fromKin(100)), exchanges[2])

        assertEquals(13, tray.slot(type = SlotType.Bucket1).partialBalance.toKinTruncatingLong())
        assertEquals(150, tray.slot(type = SlotType.Bucket10).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket100).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket10k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket100k).partialBalance.toKinTruncatingLong())
        assertEquals(0, tray.slot(type = SlotType.Bucket1m).partialBalance.toKinTruncatingLong())

        assertEquals(163.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(6_000, tray.outgoing.partialBalance.toKinTruncatingLong())
    }

    @Test
    fun testDynamicWithdrawalExample2() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)

        tray.setBalances(
            mapOf(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(0)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(4)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(1)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(9)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(1) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(6)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(1)  * SlotType.Bucket1m.getBillValue(),
            )
        )

        assertEquals(1_619_140.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.outgoing.partialBalance.toKinValueDouble(), 0.0)

        val exchanges = tray.transfer(amount = Kin.fromKin(359_804))

        assertEquals(14, exchanges.size)

        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(40)
            ),
            exchanges[0]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket100),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(100)
            ),
            exchanges[1]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket1k),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(9_000)
            ),
            exchanges[2]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10k),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(10_000)
            ),
            exchanges[3]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket100k),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(300_000)
            ),
            exchanges[4]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket100k),
                to = AccountType.Bucket(SlotType.Bucket10k),
                kin = Kin.fromKin(100_000)
            ),
            exchanges[5]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10k),
                to = AccountType.Bucket(SlotType.Bucket1k),
                kin = Kin.fromKin(10_000)
            ),
            exchanges[6]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10k),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(40_000)
            ),
            exchanges[7]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket1k),
                to = AccountType.Bucket(SlotType.Bucket100),
                kin = Kin.fromKin(1_000)
            ),
            exchanges[8]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket100),
                to = AccountType.Bucket(SlotType.Bucket10),
                kin = Kin.fromKin(100)
            ),
            exchanges[9]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket100),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(600)
            ),
            exchanges[10]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10),
                to = AccountType.Bucket(SlotType.Bucket1),
                kin = Kin.fromKin(10)
            ),
            exchanges[11]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket10),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(60)
            ),
            exchanges[12]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Bucket(SlotType.Bucket1),
                to = AccountType.Outgoing,
                kin = Kin.fromKin(4)
            ),
            exchanges[13]
        )

        assertEquals(6.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(30.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(300.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(9_000.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(50_000.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(200_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)

        assertEquals(1_259_336.0, tray.availableBalance.toKinValueDouble(), 0.0)
        assertEquals(359_804.0, tray.outgoing.partialBalance.toKinValueDouble(), 0.0)
    }

    //@Test
    fun testTransferAllPermutations() {
        var count = 0

        var tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val cleanTray = tray.copy()

        var a: Int
        var b: Int
        var c: Int
        var d: Int
        var e: Int
        var f: Int
        var g: Int

        for (i in 1..1000) {
            a = (i / 1) % 10       // <1s
            b = (i / 10) % 10      // <1s
            c = (i / 100) % 10     // ~16 sec
            d = (i / 1000) % 10    // ~28 min
            e = (i / 10000) % 10   // ~48 hours
            f = (i / 100000) % 10  // Too long
            g = (i / 1000000) % 10 // Too long

            val balances = mapOf<AccountType, Kin>(
                AccountType.Bucket(SlotType.Bucket1) to     Kin.fromKin(a)  * SlotType.Bucket1.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10) to    Kin.fromKin(b)  * SlotType.Bucket10.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100) to   Kin.fromKin(c)  * SlotType.Bucket100.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1k) to    Kin.fromKin(d)  * SlotType.Bucket1k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket10k) to   Kin.fromKin(e) * SlotType.Bucket10k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket100k) to  Kin.fromKin(f)  * SlotType.Bucket100k.getBillValue(),
                AccountType.Bucket(SlotType.Bucket1m) to    Kin.fromKin(g)  * SlotType.Bucket1m.getBillValue(),
            )

            tray = cleanTray
            tray.setBalances(balances)

            for (amount in 0 until tray.availableBalance.toKinTruncatingLong()) {
                tray = cleanTray
                tray.setBalances(balances)

                val toWithdraw = Kin.fromKin(amount + 1)
                try {
                    count += 1
//                  let exchanges = try tray.transfer(amount: toWithdraw)
                    val exchanges = tray.withdrawDynamically(amount = toWithdraw)
                        val total = exchanges.reduce { acc, exchange ->
                            if (exchange.to == AccountType.Outgoing) {
                                acc.copy(kin = acc.kin + exchange.kin.toKinTruncating())
                            } else {
                                acc
                            }
                        }
                        assertEquals(total.kin.toKinTruncating(), toWithdraw.toKinTruncating())
                    } catch(e: Exception) {
                        print("Error: ${e.message}")
                        print("Withdrawing: ${toWithdraw.toKinTruncatingLong()}")
                    }
                }
        }
        print("Total invocation count: $count")
    }

    @Test
    fun testReceiveSingleSlot() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val amount = Kin.fromKin(1_000_000)

        tray.increment(AccountType.Incoming, Kin.fromKin(1_000_000))
        val exchanges = tray.receive(AccountType.Incoming, Kin.fromKin(1_000_000))

        assertEquals(1, exchanges.size)
        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket1m),
                kin = amount
            ),
            exchanges[0]
        )

        assertEquals(0.0, tray.incoming.partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testReceiveAllSlots() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val amount = Kin.fromKin(1_234_567)

        tray.increment(AccountType.Incoming, amount)
        val exchanges = tray.receive(AccountType.Incoming, amount)

        assertEquals(7, exchanges.size)

        assertEquals(AccountType.Bucket(SlotType.Bucket1m), exchanges[0].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket100k), exchanges[1].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket10k), exchanges[2].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket1k), exchanges[3].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket100), exchanges[4].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket10), exchanges[5].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket1), exchanges[6].to)

        assertEquals(1_000_000.0, exchanges[0].kin.toKinValueDouble(), 0.0)
        assertEquals(200_000.0, exchanges[1].kin.toKinValueDouble(), 0.0)
        assertEquals(30_000.0, exchanges[2].kin.toKinValueDouble(), 0.0)
        assertEquals(4_000.0, exchanges[3].kin.toKinValueDouble(), 0.0)
        assertEquals(500.0, exchanges[4].kin.toKinValueDouble(), 0.0)
        assertEquals(60.0, exchanges[5].kin.toKinValueDouble(), 0.0)
        assertEquals(7.0, exchanges[6].kin.toKinValueDouble(), 0.0)
    }

    @Test
    fun testReceiveThreeSlots() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val amount = Kin.fromKin(1_200_500)

        tray.increment(AccountType.Incoming, amount)
        val exchanges = tray.receive(AccountType.Incoming, amount)

        assertEquals(3, exchanges.size)

        assertEquals(AccountType.Bucket(SlotType.Bucket1m), exchanges[0].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket100k), exchanges[1].to)
        assertEquals(AccountType.Bucket(SlotType.Bucket100), exchanges[2].to)

        assertEquals(0.0, tray.incoming.partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(500.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(200_000.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(1_000_000.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 0.0)
    }

    @Test
    fun testReceiveLargeAmounts() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        val amount = Kin.fromKin(95_800_173)

        tray.increment(AccountType.Incoming, amount)
        val exchanges = tray.receive(AccountType.Incoming, amount)

        assertEquals(15, exchanges.size)

        for (i in 0..9) {
            assertEquals(
                InternalExchange(
                    from = AccountType.Incoming,
                    to = AccountType.Bucket(SlotType.Bucket1m),
                    kin = Kin.fromKin(9_000_000)
                ),
                exchanges[i]
            )
        }

        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket1m),
                kin = Kin.fromKin(5_000_000)
            ),
            exchanges[10]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket100k),
                kin = Kin.fromKin(800_000)
            ),
            exchanges[11]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket100),
                kin = Kin.fromKin(100)
            ),
            exchanges[12]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket10),
                kin = Kin.fromKin(70)
            ),
            exchanges[13]
        )
        assertEquals(
            InternalExchange(
                from = AccountType.Incoming,
                to = AccountType.Bucket(SlotType.Bucket1),
                kin = Kin.fromKin(3)
            ),
            exchanges[14]
        )

        assertEquals(0.0, tray.incoming.partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1).partialBalance.toKinValueDouble(), 3.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10).partialBalance.toKinValueDouble(), 70.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket100).partialBalance.toKinValueDouble(), 100.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket10k).partialBalance.toKinValueDouble(), 0.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket100k).partialBalance.toKinValueDouble(), 800_000.0)
        assertEquals(0.0, tray.slot(SlotType.Bucket1m).partialBalance.toKinValueDouble(), 95_000_000.0)
        }

    @Test
    fun testReceiveInsufficientBalance() {
        val tray = Tray.newInstance(context = context, mnemonic = mnemonic)
        assertThrows(Tray.OrganizerException.InvalidSlotBalanceException::class.java) {
            tray.receive(AccountType.Incoming, amount = Kin.fromKin(100))
        }
    }
}