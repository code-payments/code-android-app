package com.getcode.solana.organizer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SlotTest {
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
    fun testBillCount() {
        val tray = Tray.newInstance(mnemonic)

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

        assertEquals(1, tray.slots[0].billCount())
        assertEquals(2, tray.slots[1].billCount())
        assertEquals(3, tray.slots[2].billCount())
        assertEquals(4, tray.slots[3].billCount())
        assertEquals(5, tray.slots[4].billCount())
        assertEquals(6, tray.slots[5].billCount())
        assertEquals(7, tray.slots[6].billCount())
    }

}