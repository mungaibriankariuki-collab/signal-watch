package com.signalwatch.app.debug

/** One scripted chat line for the debug "Simulate live stream" mode. */
data class SimMessage(
    val delayMs: Long,
    val author: String,
    val isOwner: Boolean,
    val isModerator: Boolean,
    val text: String,
)
