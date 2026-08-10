package com.flipcash.app.core.navigation

/**
 * Configuration for the **v1** navigation bar — the user-reorderable button order and the give
 * button label, persisted through `FeatureFlag.NavBar`.
 *
 * The v2 ("fresh coat") bar is a fixed tab set ([NavBarButton.v2Order]) with no configuration, so it
 * does not use this type at all. Keeping v1's config isolated here means `FeatureFlag.NavBar` and
 * `NavBarConfig` can be deleted together when v1 is removed, without touching the v2 path.
 */
data class NavBarConfig(
    val order: List<NavBarButton> = NavBarButton.defaultOrder,
    val giveButtonLabel: GiveButtonLabel = GiveButtonLabel.Cash,
) {
    fun serialize(): String =
        "${order.joinToString(",") { it.name }}|${giveButtonLabel.name}"

    companion object {
        val Default = NavBarConfig()

        fun deserialize(value: String): NavBarConfig {
            if (value.isBlank()) return Default
            val default = NavBarButton.defaultOrder
            val parts = value.split("|")
            val stored = parts.getOrNull(0)
                ?.split(",")
                ?.mapNotNull { runCatching { NavBarButton.valueOf(it) }.getOrNull() }
                ?.ifEmpty { default }
                ?: default
            // Back-fill any buttons added after this order was persisted (e.g. Tips),
            // inserting each at its position in defaultOrder so it lands where intended
            // rather than getting appended. Without this, a persisted order that predates
            // a new button would never surface it, even when its feature flag is enabled.
            val order = if (stored.containsAll(default)) {
                stored
            } else {
                default.fold(stored) { acc, button ->
                    if (button in acc) {
                        acc
                    } else {
                        val insertAt = default
                            .subList(0, default.indexOf(button))
                            .let { preceding -> acc.indexOfLast { it in preceding } + 1 }
                        acc.toMutableList().apply { add(insertAt, button) }
                    }
                }
            }
            val label = parts.getOrNull(1)
                ?.let { runCatching { GiveButtonLabel.valueOf(it) }.getOrNull() }
                ?: GiveButtonLabel.Give
            return NavBarConfig(order, label)
        }
    }
}
