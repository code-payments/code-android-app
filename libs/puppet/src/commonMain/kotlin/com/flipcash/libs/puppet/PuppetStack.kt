package com.flipcash.libs.puppet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The screens a host has pushed on top of its root, in order. A view model's navigation request
 * pushes a node here; the host shows the top one as focused and lends it [Back], which pops it.
 * `launch` clears it. A host has one stack, shared by every route it launches.
 */
class PuppetStack {
    private val screens = mutableListOf<PuppetNode>()

    val pushed: List<PuppetNode> get() = screens.toList()

    fun push(node: PuppetNode) {
        screens += node
    }

    fun pop() {
        screens.removeLastOrNull()
    }

    fun clear() {
        screens.clear()
    }
}

/** The action a host lends a pushed screen: return to the screen under it. */
@Serializable
@SerialName("Back")
internal data object Back
