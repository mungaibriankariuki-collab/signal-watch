package com.signalwatch.app.data.repo

import com.signalwatch.app.BuildConfig
import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.api.YouTubeApi
import com.signalwatch.app.data.quota.QuotaTracker
import com.signalwatch.app.data.rss.RssClient
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val api: YouTubeApi,
    private val rss: RssClient,
    private val quota: QuotaTracker,
    private val json: Json,
) {
    private val caller = object : ApiCaller {
        override suspend fun <T> call(cost: Int, block: suspend () -> T): T = this@YouTubeRepository.call(cost, block)
    }
    private val liveChecker = LiveChecker(api, caller) { rss.latestVideoIds(it) }

    /** channels.list?forHandle with snippet + contentDetails: still 1 unit, and gives the uploads playlist too. */
    suspend fun resolveHandle(handle: String): ChannelInfo {
        val response = call(QuotaTracker.COST_CHANNELS_LIST) { api.channelsByHandle(handle) }
        val item = response.items.firstOrNull()
            ?: throw ApiException(404, "notFound", "No channel found for $handle")
        return ChannelInfo(
            item.id,
            item.snippet?.title ?: handle,
            item.snippet?.thumbnails?.best(),
            item.contentDetails?.relatedPlaylists?.uploads,
        )
    }

    /** For a channel saved before the uploads playlist was cached (1 unit, once). */
    suspend fun uploadsPlaylistId(channelId: String): String {
        val response = call(QuotaTracker.COST_CHANNELS_LIST) { api.channelsById(channelId, part = "contentDetails") }
        return response.items.firstOrNull()?.contentDetails?.relatedPlaylists?.uploads
            ?: throw ApiException(404, "channelNotFound", "Channel not found")
    }

    /** See [LiveChecker]: uploads playlist + videos.list, about 2 units per check. */
    suspend fun findLiveVideo(channelId: String, uploadsPlaylistId: String): LiveVideo? =
        liveChecker.findLiveVideo(channelId, uploadsPlaylistId)

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
}
