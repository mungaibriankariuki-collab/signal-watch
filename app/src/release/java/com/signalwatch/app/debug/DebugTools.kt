package com.signalwatch.app.debug

/** Release stub: the simulator does not exist in release builds. */
object DebugTools {
    const val AVAILABLE = false
    const val SAMPLE_VIDEO_ID = ""

    @Suppress("UNUSED_PARAMETER")
    fun script(round: Int, ownerName: String): List<SimMessage> = emptyList()
}
