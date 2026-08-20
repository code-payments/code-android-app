package com.getcode.navigation

import androidx.navigation3.runtime.NavKey

interface Sheet: NavKey
interface WrapContentSheet: NavKey

/**
 * Opens at a half-height resting detent and can be dragged the rest of the way up, so the top of
 * the screen underneath stays visible. Content shorter than the detent bottom-aligns as usual, so
 * pair this with [WrapContentSheet] and a half-height floor if a stub sheet isn't wanted.
 */
interface HalfSheet: NavKey
interface NonDismissableRoute: NavKey
interface NonDraggableRoute: NavKey
interface SolitarySheet: NavKey
