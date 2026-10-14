package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.isSystemBlank

/**
 * The text a system notice draws and previews as, or `null` when it has none.
 *
 * The one place the notice's wording is chosen. When the server sends a structured event this
 * client can localise, it becomes `when (event) { known -> localised; else -> fallbackText }` here,
 * with a resource lookup as a parameter, and no caller changes.
 *
 * Blank is [isSystemBlank], the definition iOS shares. Text that is not blank is returned as it
 * arrived: not trimmed. Rows stored before the mapper dropped blank notices still reach this.
 */
fun MessageContent.System.displayText(): String? = fallbackText.takeUnless { it.isSystemBlank() }
