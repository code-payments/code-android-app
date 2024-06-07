package dev.bmcreations.tipkit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bmcreations.tipkit.engines.EligibilityCriteria
import dev.bmcreations.tipkit.engines.Event
import dev.bmcreations.tipkit.engines.EventEngine
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.bmcreations.tipkit.engines.Trigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine

abstract class Tip(
    private val engine: EventEngine,
    private val tipEngine: TipsEngine
) {
    val name: String
        get() = this::class.java.simpleName.lowercase()

    // region flow control
    private var wasHidden: Boolean = false

    var flowPosition: Int = 0
    var flowId: String? = null
        set(value) {
            if (value != null) {
                tipEngine.associateTipWithFlow(this, flowPosition, value)
            }
            field = value
        }

    val flow: List<Tip>
        get() = flowId?.let {
            tipEngine.flows[it]
    }.orEmpty()
    // endregion

    private val triggers = mutableListOf<Trigger>()
    private val events: Flow<List<Event.TriggerOccurrence>>
        get() {
            val flows = triggers.map { it.events }
            return combine(*flows.toTypedArray()) { it.toList().flatten() }
        }

    /**
     * Registers the [trigger] for watching events
     */
    open fun await(trigger: Trigger) = triggers.add(trigger.copy(id = "$name-${trigger.id}"))

    // region UI configuration
    open fun asset(): @Composable () -> Unit = {}
    open fun title(): @Composable () -> Unit = {}
    open fun message(): @Composable () -> Unit = {}
    open fun image(): @Composable () -> Unit = {}
    open fun showClose(): Boolean = true
    open fun actions(): List<TipAction> = emptyList()
    // endregion

    /**
     * event stream for [triggers]
     */
    open fun observe(): Flow<List<Event>> = events

    val flowContinuation: MutableSharedFlow<Unit> = MutableSharedFlow()

    /**
     * Eligibility criteria for whether this tip should show
     */
    open suspend fun criteria(): List<EligibilityCriteria> = listOf { true }

    /**
     * Triggers a check of all [criteria] and if so, the Modifier will pass the [TipLocation] to the [TipProvider]
     * for display.
     */
    suspend fun show(): Boolean {
        println("show tip?")
        return criteria().all { it() }
    }

    /**
     * Whether or not this tip has been displayed
     */
    suspend fun hasBeenSeen(): Boolean {
        if (flow.isNotEmpty()) {
            return wasHidden
        }
        return engine.isComplete(name)
    }

    /**
     * Marks the tip completed
     */
    suspend fun dismiss() {
        if (flow.isNotEmpty()) {
            // if all other tips (not including this one) have been seen, then we can mark them all as hidden
            if (flow.all { it.hasBeenSeen() }) {
                engine.complete(name)
            } else {
                wasHidden = true
                // trigger show of next tip in flow
                flow.getOrNull(flowPosition + 1)?.flowContinuation?.emit(Unit)
            }
        } else {
            engine.complete(name)
        }
    }
}

data class TipAction(val tipId: String, val id: String, val title: String)

val TipDefaultAlignment: Alignment = Alignment.CenterStart
val TipDefaultPadding: PaddingValues = PaddingValues(20.dp, 10.dp)
