package com.getcode.util

import com.getcode.utils.blockchainMemo
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class UUIDTests {
    @Test
    fun testMemo() {
        val uuid = UUID.fromString("c24a3bf2-ad4f-4756-944e-81948ff10882")
        val memo = uuid.blockchainMemo

        assertEquals(
            "AQAAAAAAwko78q1PR1aUToGUj/EIgg==",
            memo
        )
    }
}