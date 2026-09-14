package com.github.daanbouwman.flightplanner.ui

/**
 * How long a ViewModel's `WhileSubscribed` flow keeps running after its last
 * collector goes away.
 *
 * Long enough to survive a configuration change — the screen is recomposed and
 * re-subscribes within a frame or two, and a flow that stopped in between would
 * restart cold and flash its initial value — and short enough that a
 * backgrounded app stops observing the database within a few seconds.
 *
 * It used to be eight private copies of the same `5_000L`, each commented
 * "matches X's own constant", which is the shape a retune goes wrong in. One
 * name, one value, every ViewModel.
 */
internal const val STOP_TIMEOUT_MILLIS = 5_000L
