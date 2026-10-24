package com.getcode.util.resources

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * Reads the string behind a resource ID in composition. On Android these are `R.string` values;
 * on desktop they come from [ResourceHelper.getIdentifier] or a module's string table.
 */
@Composable
expect fun stringRes(@StringRes id: Int): String

@Composable
expect fun stringRes(@StringRes id: Int, vararg formatArgs: Any): String

@Composable
expect fun pluralStringRes(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String
