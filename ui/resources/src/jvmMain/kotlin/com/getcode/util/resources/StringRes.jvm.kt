package com.getcode.util.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun stringRes(id: Int): String = stringResource(ComposeResourceIds.string(id))

@Composable
actual fun stringRes(id: Int, vararg formatArgs: Any): String =
    stringResource(ComposeResourceIds.string(id), *formatArgs)

@Composable
actual fun pluralStringRes(id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(ComposeResourceIds.plural(id), count, *formatArgs)
