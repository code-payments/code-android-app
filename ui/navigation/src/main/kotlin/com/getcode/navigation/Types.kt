package com.getcode.navigation

import androidx.navigation3.runtime.NavKey

interface Sheet: NavKey
interface WrapContentSheet: NavKey

/**
 * Rests at half the screen height, so the top of the screen underneath stays visible.
 *
 * The sheet fills that height whatever the content's own height is, so content should size itself
 * to what it is given rather than restating the fraction. It is draggable further up only while the
 * content reports it has something below the fold — see
 * [com.getcode.ui.utils.AllowSheetExpansionWhenScrollable].
 */
interface HalfSheet: NavKey
interface NonDismissableRoute: NavKey
interface NonDraggableRoute: NavKey
interface SolitarySheet: NavKey
