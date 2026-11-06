package com.getcode.view

/**
 * Marks a [BaseViewModel] event that the screen sends, as opposed to an internal message such as a
 * feed update. A view model's single `Event` type holds both; tooling that lists what a screen can do
 * (the headless Reflex host in `apps/desktop`) lists only the events that implement this.
 *
 * Reducers don't read it.
 */
interface PublicAction
