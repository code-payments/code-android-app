package com.flipcash.app.core.navigation

data class NavBarConfig(
    val order: List<NavBarButton> = NavBarButton.defaultOrder,
    val giveButtonLabel: GiveButtonLabel = GiveButtonLabel.Give,
) {
    fun serialize(): String =
        "${order.joinToString(",") { it.name }}|${giveButtonLabel.name}"

    companion object {
        val Default = NavBarConfig()

        fun deserialize(value: String): NavBarConfig {
            if (value.isBlank()) return Default
            val parts = value.split("|")
            val order = parts.getOrNull(0)
                ?.split(",")
                ?.mapNotNull { runCatching { NavBarButton.valueOf(it) }.getOrNull() }
                ?.ifEmpty { NavBarButton.defaultOrder }
                ?: NavBarButton.defaultOrder
            val label = parts.getOrNull(1)
                ?.let { runCatching { GiveButtonLabel.valueOf(it) }.getOrNull() }
                ?: GiveButtonLabel.Give
            return NavBarConfig(order, label)
        }
    }
}
