package com.getcode.opencode.solana.intents

import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.opencode.solana.intents.actions.numberActions

class ActionGroupBuilder {
    private val actions = mutableListOf<ActionType>()

    // Function to add an action
    fun add(action: ActionType) {
        actions.add(action)
    }

    // Build the ActionGroup instance
    fun build(): ActionGroup {
        return ActionGroup(actions.numberActions())
    }
}

// ActionGroup class
class ActionGroup(actions: List<ActionType> = emptyList()) {
    var actions: List<ActionType> = actions.numberActions()
        set(value) {
            field = value.numberActions()
        }
}

// Top-level function to create ActionGroup using the builder
inline fun buildActionGroup(block: ActionGroupBuilder.() -> Unit): ActionGroup {
    return ActionGroupBuilder().apply(block).build()
}