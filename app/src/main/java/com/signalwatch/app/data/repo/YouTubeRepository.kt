package com.signalwatch.app.data.repo

import com.signalwatch.app.BuildConfig
import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.api.VideoItem
import com.signalwatch.app.data.api.YouTubeApi
import com.signalwatch.app.data.quota.QuotaTracker
import com.signalwatch.app.data.rss.RssClient
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val api: YouTubeApi,
    private val rss: RssClient,
    private val quota: QuotaTracker,
    private val json: Json,
) {
    /** Videos already known to be finished uploads/VODs; they can never go live, so we stop asking. */
    private val finished: MutableSet<String> = Collections.synchronizedSet(HashSet())

    suspend fun resolveHandle(handle: String): ChannelInfo {
        val response = call(QuotaTracker.COST_CHANNELS_LIST) { api.channelsByHandle(handle) }
        val item = response.items.firstOrNull()
            ?: throw ApiException(404, "notFound", "No channel found for $handle")
        return ChannelInfo(item.id, item.snippet?.title ?: handle, item.snippet?.thumbnails?.best())
    }

    /**
     * Live check without search.list: read the free RSS feed, then ask videos.list (1 unit,
     * up to 50 ids per call) about the newest few videos we don't already know are finished.
     */
    suspend fun findLiveVideo(channelId: String): LiveVideo? {
        val candidates = rss.latestVideoIds(channelId).take(RSS_CANDIDATES).filterNot { it in finished }
        if (candidates.isEmpty()) return null
        val response = call(QuotaTracker.COST_VIDEOS_LIST) { api.videos(candidates.joinToString(",")) }
        response.items.forEach { if (it.snippet?.liveBroadcastContent == "none") finished += it.id }
        return response.items.map { it.toLiveVideo() }.firstOrNull { it.isLive }
    }

    suspend fun video(videoId: String): LiveVideo? =
        call(QuotaTracker.COST_VIDEOS_LIST) { api.videos(videoId) }.items.firstOrNull()?.toLiveVideo()

    suspend fun chatPage(liveChatId: String, pageToken: String?): ChatPage {
        val response = call(QuotaTracker.COST_LIVE_CHAT_MESSAGES_LIST) {
            api.liveChatMessages(liveChatId, pageToken)
        }
        var ended = response.offlineAt != null
        val messages = response.items.mapNotNull { m ->
            val snippet = m.snippet ?: return@mapNotNull null
            if (snippet.type == "chatEndedEvent") ended = true
            val text = snippet.textMessageDetails?.messageText ?: snippet.displayMessage
            if (text.isNullOrBlank()) return@mapNotNull null
            val author = m.authorDetails
            RawMessage(
                id = m.id,
                author = author?.displayName.orEmpty(),
                isOwner = author?.isChatOwner == true,
                isModerator = author?.isChatModerator == true,
                text = text,
                publishedAt = parseTime(snippet.publishedAt) ?: Instant.now(),
            )
        }
        return ChatPage(messages, response.nextPageToken, response.pollingIntervalMillis, ended)
    }

    private suspend fun <T> call(cost: Int, block: suspend () -> T): T {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            throw ApiException(0, "noApiKey", "No API key. Add YOUTUBE_API_KEY to local.properties and rebuild.")
        }
        quota.add(cost)
        return try {
            block()
        } catch (e: HttpException) {
            throw ApiException.from(e, json)
        }
    }

    private fun VideoItem.toLiveVideo(): LiveVideo {
        val details = liveStreamingDetails
        val live = snippet?.liveBroadcastContent == "live" &&
            details?.actualStartTime != null && details.actualEndTime == null
        return LiveVideo(
            videoId = id,
            title = snippet?.title.orEmpty(),
            startedAt = parseTime(details?.actualStartTime),
            liveChatId = details?.activeLiveChatId,
            isLive = live,
        )
    }

    private fun parseTime(value: String?): Instant? =
        value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    private companion object {
        const val RSS_CANDIDATES = 5
    }
}
