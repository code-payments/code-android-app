package com.getcode.ui.components.text

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumberInputHelperTest {

    private lateinit var helper: NumberInputHelper

    @Before
    fun setUp() {
        helper = NumberInputHelper()
        helper.reset()
    }

    // region onNumber

    @Test
    fun `onNumber appends digit`() {
        helper.onNumber(5)
        assertEquals(5.0, helper.amount)
    }

    @Test
    fun `onNumber replaces leading zero`() {
        helper.onNumber(3)
        assertEquals(3.0, helper.amount)
        assertEquals("3", helper.getFormattedString())
    }

    @Test
    fun `onNumber enforces maxLength`() {
        helper.maxLength = 3
        helper.onNumber(1)
        helper.onNumber(2)
        helper.onNumber(3)
        helper.onNumber(4) // should be ignored
        assertEquals(123.0, helper.amount)
    }

    @Test
    fun `onNumber enforces fractionUnits limit`() {
        helper.onNumber(1)
        helper.onDot()
        helper.onNumber(2)
        helper.onNumber(3)
        helper.onNumber(4) // should be ignored, fractionUnits defaults to 2
        assertEquals(1.23, helper.amount)
    }

    @Test
    fun `onNumber builds multi-digit number`() {
        helper.onNumber(1)
        helper.onNumber(0)
        helper.onNumber(0)
        assertEquals(100.0, helper.amount)
    }

    // endregion

    // region onDot

    @Test
    fun `onDot appends decimal separator`() {
        helper.onNumber(1)
        helper.onDot()
        helper.onNumber(5)
        assertEquals(1.5, helper.amount)
    }

    @Test
    fun `onDot rejected when fractionUnits is 0`() {
        helper.fractionUnits = 0
        helper.onNumber(1)
        helper.onDot()
        helper.onNumber(5)
        assertEquals(15.0, helper.amount)
    }

    @Test
    fun `onDot rejected when already present`() {
        helper.onNumber(1)
        helper.onDot()
        helper.onDot() // second dot ignored
        helper.onNumber(5)
        assertEquals(1.5, helper.amount)
    }

    // endregion

    // region onBackspace

    @Test
    fun `onBackspace removes last character`() {
        helper.onNumber(1)
        helper.onNumber(2)
        helper.onBackspace()
        assertEquals(1.0, helper.amount)
    }

    @Test
    fun `onBackspace on single digit resets to zero`() {
        helper.onNumber(5)
        helper.onBackspace()
        assertEquals(0.0, helper.amount)
    }

    @Test
    fun `onBackspace on zero is no-op`() {
        helper.onBackspace()
        assertEquals(0.0, helper.amount)
    }

    // endregion

    // region reset

    @Test
    fun `reset clears to zero`() {
        helper.onNumber(9)
        helper.onNumber(9)
        helper.reset()
        assertEquals(0.0, helper.amount)
    }

    // endregion

    // region getFormattedString

    @Test
    fun `getFormattedString adds commas for large numbers`() {
        helper.onNumber(1)
        helper.onNumber(2)
        helper.onNumber(3)
        helper.onNumber(4)
        helper.onNumber(5)
        assertEquals("12,345", helper.getFormattedString())
    }

    @Test
    fun `getFormattedString shows decimals when dot present`() {
        helper.onNumber(1)
        helper.onDot()
        helper.onNumber(5)
        assertEquals("1.50", helper.getFormattedString())
    }

    @Test
    fun `getFormattedString no decimals for whole numbers`() {
        helper.onNumber(4)
        helper.onNumber(2)
        assertEquals("42", helper.getFormattedString())
    }

    // endregion

    // region getFormattedStringForAnimation

    @Test
    fun `getFormattedStringForAnimation includes comma visibility`() {
        helper.onNumber(1)
        helper.onNumber(2)
        helper.onNumber(3)
        helper.onNumber(4)
        val data = helper.getFormattedStringForAnimation(includeCommas = true)
        // "1234" without commas, comma visibility should mark positions
        assertEquals("1234", data.amountText)
        assertTrue(data.commaVisibility.any { it })
    }

    @Test
    fun `getFormattedStringForAnimation without commas`() {
        helper.onNumber(1)
        helper.onNumber(2)
        helper.onNumber(3)
        helper.onNumber(4)
        val data = helper.getFormattedStringForAnimation(includeCommas = false)
        assertEquals("1234", data.amountText)
        assertTrue(data.commaVisibility.none { it })
    }

    // endregion

    // region AmountAnimatedData

    @Test
    fun `AmountAnimatedData isEmpty returns true for empty text`() {
        val data = NumberInputHelper.AmountAnimatedData(amountText = "")
        assertTrue(data.isEmpty())
    }

    @Test
    fun `AmountAnimatedData isEmpty returns false for non-empty text`() {
        val data = NumberInputHelper.AmountAnimatedData(amountText = "5")
        assertFalse(data.isEmpty())
    }

    // endregion
}
