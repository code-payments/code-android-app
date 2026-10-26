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
 */
internal data class TimeToPay(
    val now: Instant,
    val zone: ZoneId,
    val choice: Choice,
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

    /** Calendar days from today to the custom day. */
    val daysFromNow: Long get() = ChronoUnit.DAYS.between(today, selectedDay)

    fun choosePreset(preset: Deadline.Preset): TimeToPay = copy(choice = Choice.Preset(preset))

    /**
     * Makes [day] the custom choice, keeping the last custom time or 6:00 PM, moved inside the
     * custom bounds.
     */
    fun chooseDay(day: LocalDate): TimeToPay {
        if (!enabled(day)) return this
        val time = (choice as? Choice.Custom)?.time ?: DEFAULT_TIME
        return copy(choice = clamped(day, time))
    }

    /** The Time row's new time for the custom day, moved inside the custom bounds. */
    fun chooseTime(time: LocalTime): TimeToPay {
        val custom = choice as? Choice.Custom ?: return this
        return copy(choice = clamped(custom.day, time))
    }

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
            return TimeToPay(now, zone, choice)
        }

        /** The grid's 35 days, from the locale's first weekday of the week that holds [today]. */
        fun grid(today: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
            val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return List(GRID_DAYS) { start.plusDays(it.toLong()) }
        }
    }
}
