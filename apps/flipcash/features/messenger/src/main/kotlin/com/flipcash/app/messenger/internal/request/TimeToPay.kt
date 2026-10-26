package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * What the Time To Pay sheet has chosen so far, before Done applies it to the draft. Swiping the
 * sheet away drops it, so the draft keeps its previous [Deadline].
 *
 * @param now when the sheet opened; the grid, the custom bounds and the readout are measured from it.
 * @param setTime the last time the user set in the Time row, which a tapped day keeps.
 */
internal data class TimeToPay(
    val now: Instant,
    val zone: ZoneId,
    val choice: Choice,
    val setTime: LocalTime? = null,
) {
    sealed interface Choice {
        data class Preset(val preset: Deadline.Preset) : Choice
        data class Custom(val day: LocalDate, val time: LocalTime) : Choice
    }

    val today: LocalDate get() = now.local().toLocalDate()

    /** The last day the grid enables: today + 28 days. */
    val lastDay: LocalDate get() = today.plusDays(LAST_DAY_OFFSET)

    fun enabled(day: LocalDate): Boolean = !day.isBefore(today) && !day.isAfter(lastDay)

    /** The grid's highlighted day: a preset's "around" date, or the custom day. */
    val selectedDay: LocalDate
        get() = when (choice) {
            is Choice.Preset -> choice.preset.endsAt(now).local().toLocalDate()
            is Choice.Custom -> choice.day
        }

    /** The custom choice as an instant, or null for a preset. */
    val customAt: Instant?
        get() = (choice as? Choice.Custom)?.let { ZonedDateTime.of(it.day, it.time, zone).toInstant().toKotlinInstant() }

    /** How [day] draws in the range from today to [selectedDay]. */
    fun range(day: LocalDate): RangeCell {
        val end = selectedDay
        return when {
            day == end -> RangeCell(RangeCell.Mark.End, leading = day != today, trailing = false)
            day == today -> RangeCell(RangeCell.Mark.Start, leading = false, trailing = true)
            day.isAfter(today) && day.isBefore(end) -> RangeCell(RangeCell.Mark.None, leading = true, trailing = true)
            else -> RangeCell.Outside
        }
    }

    /** The tone of [day]'s month label, for a 1st of the month. */
    fun monthTone(day: LocalDate): MonthTone {
        val cell = range(day)
        return when {
            cell.mark == RangeCell.Mark.End -> MonthTone.End
            cell.mark == RangeCell.Mark.Start -> MonthTone.Start
            cell.leading && cell.trailing -> MonthTone.Band
            else -> MonthTone.Plain
        }
    }

    /**
     * One week row's part of the range band, from [from] to [to] in columns from the row's start.
     * An end is rounded only where the range wraps past the row's edge; where it meets the start or
     * end circle it is square, so the band runs straight up to the circle.
     */
    data class BandSpan(
        val row: Int,
        val from: Float,
        val to: Float,
        val roundFrom: Boolean = false,
        val roundTo: Boolean = false,
    )

    /** A month label's colour: on no range, inside the band, on the closing circle, on today's circle. */
    enum class MonthTone { Plain, Band, End, Start }

    /**
     * One grid day's part of the range: its circle, and which halves the band covers. The band
     * runs from the centre of the start circle to the centre of the end circle.
     */
    data class RangeCell(val mark: Mark, val leading: Boolean, val trailing: Boolean) {
        enum class Mark { None, Start, End }

        companion object {
            val Outside = RangeCell(Mark.None, leading = false, trailing = false)
        }
    }

    /**
     * The Time row's value. A preset closes at the time of day the request is sent, so it reads
     * the current time.
     */
    val time: LocalTime
        get() = when (choice) {
            is Choice.Preset -> now.local().toLocalTime().truncatedTo(ChronoUnit.MINUTES)
            is Choice.Custom -> choice.time
        }

    /** Calendar days from today to the custom day. */
    val daysFromNow: Long get() = ChronoUnit.DAYS.between(today, selectedDay)

    fun choosePreset(preset: Deadline.Preset): TimeToPay = copy(choice = Choice.Preset(preset))

    /**
     * Makes [day] the custom choice, keeping the last time the user set or 6:00 PM, moved inside
     * the custom bounds.
     */
    fun chooseDay(day: LocalDate): TimeToPay {
        if (!enabled(day)) return this
        return copy(choice = clamped(day, setTime ?: DEFAULT_TIME))
    }

    /**
     * The Time row's new time, moved inside the custom bounds. On a preset it clears the chip:
     * the preset's closing day at [time] becomes the custom choice.
     */
    fun chooseTime(time: LocalTime): TimeToPay =
        copy(choice = clamped(selectedDay, time), setTime = time.truncatedTo(ChronoUnit.MINUTES))

    /** What Done applies to the draft. */
    fun toDeadline(): Deadline = when (choice) {
        is Choice.Preset -> choice.preset
        is Choice.Custom -> Deadline.Custom(customAt!!)
    }

    private fun clamped(day: LocalDate, time: LocalTime): Choice.Custom {
        val range = Deadline.customRange(now)
        // Rounded to the minute inward, so the bound itself stays inside the range.
        val earliest = range.start.local().let { if (it.second == 0 && it.nano == 0) it else it.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1) }
        val latest = range.endInclusive.local().truncatedTo(ChronoUnit.MINUTES)
        val at = ZonedDateTime.of(day, time.truncatedTo(ChronoUnit.MINUTES), zone)
        val bounded = when {
            at.isBefore(earliest) -> earliest
            at.isAfter(latest) -> latest
            else -> at
        }
        return Choice.Custom(bounded.toLocalDate(), bounded.toLocalTime())
    }

    private fun Instant.local(): ZonedDateTime = toJavaInstant().atZone(zone)

    companion object {
        const val GRID_DAYS = 35
        private const val LAST_DAY_OFFSET = 28L
        val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)

        /** The sheet reopened on the draft's current [deadline]: its chip, or its day and time. */
        fun of(deadline: Deadline, now: Instant, zone: ZoneId): TimeToPay {
            val choice = when (deadline) {
                is Deadline.Preset -> Choice.Preset(deadline)
                is Deadline.Custom -> deadline.at.toJavaInstant().atZone(zone).let {
                    Choice.Custom(it.toLocalDate(), it.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                }
            }
            return TimeToPay(now, zone, choice, setTime = (choice as? Choice.Custom)?.time)
        }

        /** The grid's 35 days, from the locale's first weekday of the week that holds [today]. */
        /**
         * The range band as one span per week row, in column units, from the centre of the start
         * cell at [startIndex] to the centre of the (possibly moving) end at [endIndex]. A range
         * that wraps ends at one row's edge and starts again at the next row's edge. Drawing a
         * span per row rather than a piece per cell keeps a moving band free of seams.
         */
        fun bandSpans(startIndex: Int, endIndex: Float): List<BandSpan> {
            val from = startIndex + 0.5f
            val to = endIndex + 0.5f
            if (to <= from) return emptyList()
            return (startIndex / 7..(to.toInt().coerceAtMost(GRID_DAYS - 1)) / 7).mapNotNull { row ->
                val rowStart = row * 7f
                val start = maxOf(from, rowStart)
                val end = minOf(to, rowStart + 7)
                if (end > start) {
                    BandSpan(
                        row = row,
                        from = start - rowStart,
                        to = end - rowStart,
                        roundFrom = from < rowStart,
                        roundTo = to > rowStart + 7,
                    )
                } else {
                    null
                }
            }
        }

        fun grid(today: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
            val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return List(GRID_DAYS) { start.plusDays(it.toLong()) }
        }
    }
}
