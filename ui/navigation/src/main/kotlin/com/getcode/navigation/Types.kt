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
/**
 * Rests at the full height of the screen, status bar included.
 *
 * Every other sheet stops at 92.5%, which leaves the screen behind it visible as a reminder that
 * the sheet is a layer over something. A route that is a screen in its own right — one someone
 * works through rather than glances at — has nothing to gain from that sliver and loses the room
 * it costs. [com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy] squares off the top
 * corners and insets the content past the status bar for these, so the result reads as a screen
 * that arrived from the bottom rather than a card wedged against the top of the display.
 *
 * Declare it alongside [Sheet]; on its own it does nothing, exactly as [HalfSheet] does not.
 */
interface FullscreenSheet: NavKey
interface NonDismissableRoute: NavKey
interface NonDraggableRoute: NavKey
interface SolitarySheet: NavKey
