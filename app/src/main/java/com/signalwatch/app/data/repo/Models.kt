package com.signalwatch.app.data.repo

import java.time.Instant

data class ChannelInfo(val id: String, val title: String, val thumbUrl: String?)

data class LiveVideo(
    val videoId: String,
    val title: String,
    val startedAt: Instant?,
    val liveChatId: String?,
    val isLive: Boolean,
    val simulated: Boolean = false,
)

/** A chat message reduced to what the app needs. */
data class RawMessage(
    val id: String,
    val author: String,
    val isOwner: Boolean,
    val isModerator: Boolean,
    val text: String,
    val publishedAt: Instant,
)

data class ChatPage(
    val messages: List<RawMessage>,
    val nextPageToken: String?,
    val pollingIntervalMillis: Long,
    /** True when the response says the broadcast went offline / chat ended. */
    val ended: Boolean,
)
