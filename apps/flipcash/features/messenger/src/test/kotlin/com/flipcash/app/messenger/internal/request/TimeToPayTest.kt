package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.app.messenger.internal.request.TimeToPay.Choice
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import com.flipcash.app.messenger.internal.request.TimeToPay.RangeCell
import com.flipcash.app.messenger.internal.request.TimeToPay.RangeCell.Mark
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.toKotlinInstant

/** Spec "Deadline sheet", tests 34–40. UTC so 28 days never cross a clock change. */
class TimeToPayTest {
    private val zone = ZoneOffset.UTC

    private fun openedAt(month: Int, day: Int, hour: Int, minute: Int, deadline: Deadline = Deadline.Week) =
        TimeToPay.of(
            deadline,
            LocalDateTime.of(2026, month, day, hour, minute).toInstant(zone).toKotlinInstant(),
            zone,
        )

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `test 34 - default sheet on Mon Oct 26 with Sunday weeks`() {
        val sheet = openedAt(10, 26, 11, 39)
        val grid = TimeToPay.grid(sheet.today, DayOfWeek.SUNDAY)

        assertEquals(35, grid.size)
        assertEquals(date(10, 25), grid.first())
        assertFalse(sheet.enabled(date(10, 25)))
        assertTrue(sheet.enabled(date(10, 26)))
        assertTrue(sheet.enabled(date(11, 23)))
        assertFalse(sheet.enabled(date(11, 24)))
        assertEquals(Choice.Preset(Deadline.Preset.Week), sheet.choice)
        assertEquals(date(11, 2), sheet.selectedDay)
        assertNull(sheet.customAt)
        assertEquals(RangeCell(Mark.Start, leading = false, trailing = true), sheet.range(date(10, 26)))
        // The band wraps from Sat Oct 31 to Sun Nov 1; the grid rounds it at the row edges.
        assertEquals(RangeCell(Mark.None, leading = true, trailing = true), sheet.range(date(10, 31)))
        assertEquals(RangeCell(Mark.None, leading = true, trailing = true), sheet.range(date(11, 1)))
        assertEquals(RangeCell(Mark.End, leading = true, trailing = false), sheet.range(date(11, 2)))
        assertEquals(RangeCell.Outside, sheet.range(date(10, 25)))
        assertEquals(RangeCell.Outside, sheet.range(date(11, 3)))
    }

    @Test
    fun `test 35 - tapping Nov 5 makes a custom 6 PM choice 10 days out`() {
        val sheet = openedAt(10, 26, 11, 39).chooseDay(date(11, 5))

        assertEquals(Choice.Custom(date(11, 5), LocalTime.of(18, 0)), sheet.choice)
        assertEquals(10, sheet.daysFromNow)
        assertEquals(RangeCell(Mark.None, leading = true, trailing = true), sheet.range(date(11, 4)))
        assertEquals(RangeCell(Mark.End, leading = true, trailing = false), sheet.range(date(11, 5)))
    }

    @Test
    fun `test 36 - today at 5_30 PM moves 6 PM to now plus 1 hour`() {
        val sheet = openedAt(10, 26, 17, 30).chooseDay(date(10, 26))

        assertEquals(Choice.Custom(date(10, 26), LocalTime.of(18, 30)), sheet.choice)
    }

    @Test
    fun `test 37 - the last day moves 6 PM to now plus 28 days less 5 minutes`() {
        val sheet = openedAt(10, 26, 11, 39).chooseDay(date(11, 23))

        assertEquals(Choice.Custom(date(11, 23), LocalTime.of(11, 34)), sheet.choice)
    }

    @Test
    fun `test 38 - a chip after a custom day highlights its date`() {
        val sheet = openedAt(10, 26, 11, 39).chooseDay(date(11, 5)).choosePreset(Deadline.Preset.TwoWeeks)

        assertEquals(Choice.Preset(Deadline.Preset.TwoWeeks), sheet.choice)
        assertEquals(date(11, 9), sheet.selectedDay)
        assertEquals(Deadline.Preset.TwoWeeks, sheet.toDeadline())
        assertEquals(RangeCell(Mark.End, leading = true, trailing = false), sheet.range(date(11, 9)))
        assertEquals(RangeCell.Outside, sheet.range(date(11, 10)))
    }

    @Test
    fun `test 40 - today tapped shows only the end circle`() {
        val sheet = openedAt(10, 26, 11, 39).chooseDay(date(10, 26))

        assertEquals(Choice.Custom(date(10, 26), LocalTime.of(18, 0)), sheet.choice)
        assertEquals(RangeCell(Mark.End, leading = false, trailing = false), sheet.range(date(10, 26)))
        assertEquals(RangeCell.Outside, sheet.range(date(10, 27)))
    }

    @Test
    fun `test 39 - a custom day the sheet never applies leaves the draft's deadline`() {
        val draft = Deadline.Week
        openedAt(10, 26, 11, 39, draft).chooseDay(date(11, 5))

        // Only Done calls toDeadline(); reopening on the untouched draft shows the chip again.
        assertEquals(Choice.Preset(Deadline.Preset.Week), openedAt(10, 26, 11, 40, draft).choice)
    }

    @Test
    fun `a changed day keeps the last custom time`() {
        val sheet = openedAt(10, 26, 11, 39)
            .chooseDay(date(11, 5))
            .chooseTime(LocalTime.of(9, 15))
            .chooseDay(date(11, 7))

        assertEquals(Choice.Custom(date(11, 7), LocalTime.of(9, 15)), sheet.choice)
    }

    @Test
    fun `a disabled day is ignored`() {
        val sheet = openedAt(10, 26, 11, 39)

        assertEquals(sheet, sheet.chooseDay(date(11, 24)))
    }

    @Test
    fun `reopening a custom deadline shows its day and time`() {
        val at = LocalDateTime.of(2026, 11, 5, 18, 0).toInstant(zone).toKotlinInstant()
        val sheet = openedAt(10, 26, 12, 0, Deadline.Custom(at))

        assertEquals(Choice.Custom(date(11, 5), LocalTime.of(18, 0)), sheet.choice)
        assertEquals(Deadline.Custom(at), sheet.toDeadline())
    }
}
