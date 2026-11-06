package com.flipcash.libs.reflex

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/**
 * One screen as the headless host sees it: its [name], its state as JSON, the actions it accepts,
 * and the screen it is showing on top of itself, if any. The host follows [child] to the focused
 * screen.
 */
class ReflexNode(
    val name: String,
    private val state: () -> JsonElement,
    val actions: List<ReflexAction<*>>,
    private val child: () -> ReflexNode? = { null },
) {
    fun state(): JsonElement = state.invoke()

    fun child(): ReflexNode? = child.invoke()

    fun action(name: String): ReflexAction<*>? = actions.firstOrNull { it.name == name }
}

/**
 * An action a screen accepts. Its [name] is the serializer's serial name, so the action type needs
 * `@SerialName` with the short name both platforms use; its input schema comes from the same
 * serializer's descriptor.
 */
class ReflexAction<A>(
    val serializer: KSerializer<A>,
    private val perform: (A) -> Unit,
) {
    val name: String get() = serializer.descriptor.serialName

    /** Decodes [input], throwing on a mismatch, and returns the call that performs the action. */
    internal fun bind(input: JsonElement): () -> Unit {
        val decoded = ReflexJson.input.decodeFromJsonElement(serializer, input)
        return { perform(decoded) }
    }
}
