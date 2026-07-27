package com.flipcash.app.core.navigation

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
            val parts = value.split("|")
            val stored = parts.getOrNull(0)
                ?.split(",")
                ?.mapNotNull { runCatching { NavBarButton.valueOf(it) }.getOrNull() }
                ?.ifEmpty { NavBarButton.defaultOrder }
                ?: NavBarButton.defaultOrder
            // Back-fill any buttons added after this order was persisted (e.g. Tips),
            // inserting each at its position in defaultOrder so it lands where intended
            // rather than getting appended. Without this, a persisted order that predates
            // a new button would never surface it, even when its feature flag is enabled.
            val order = if (stored.containsAll(NavBarButton.defaultOrder)) {
                stored
            } else {
                NavBarButton.defaultOrder.fold(stored) { acc, button ->
                    if (button in acc) {
                        acc
                    } else {
                        val insertAt = NavBarButton.defaultOrder
                            .subList(0, NavBarButton.defaultOrder.indexOf(button))
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
