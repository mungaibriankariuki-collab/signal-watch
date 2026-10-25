package com.signalwatch.app.data.repo

import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.api.VideoItem
import com.signalwatch.app.data.api.YouTubeApi
import com.signalwatch.app.data.quota.QuotaTracker
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Collections

/** Runs one Data API call: checks the key, counts its quota cost, and turns HTTP errors into [ApiException]. */
interface ApiCaller {
    suspend fun <T> call(cost: Int, block: suspend () -> T): T
}

/**
 * Live check without search.list. The channel's uploads playlist (playlistItems.list, 1 unit) gives the
 * newest video IDs, including live and upcoming broadcasts; videos.list (1 unit, up to 50 IDs) says which,
 * if any, is live right now.
 *
 * The RSS feed is only a fallback for the IDs when the playlist call fails. It can't help once the quota is
 * used up, because the live status still needs videos.list.
 */
class LiveChecker(
    private val api: YouTubeApi,
    private val caller: ApiCaller,
    private val rssVideoIds: suspend (channelId: String) -> List<String>,
) {
    /** Videos already known to be finished uploads/VODs; they can never go live, so we stop asking. */
    private val finished: MutableSet<String> = Collections.synchronizedSet(HashSet())

    suspend fun findLiveVideo(channelId: String, uploadsPlaylistId: String): LiveVideo? {
        val candidates = latestVideoIds(channelId, uploadsPlaylistId).take(CANDIDATES).filterNot { it in finished }
        if (candidates.isEmpty()) return null
        val response = caller.call(QuotaTracker.COST_VIDEOS_LIST) { api.videos(candidates.joinToString(",")) }
        response.items.forEach { if (it.snippet?.liveBroadcastContent == "none") finished += it.id }
        return response.items.map { it.toLiveVideo() }.firstOrNull { it.isLive }
    }

    private suspend fun latestVideoIds(channelId: String, uploadsPlaylistId: String): List<String> {
        val primary = try {
            return caller.call(QuotaTracker.COST_PLAYLIST_ITEMS_LIST) { api.playlistItems(uploadsPlaylistId) }
                .items.mapNotNull { it.contentDetails?.videoId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        if (primary is ApiException && primary.isQuotaExceeded) throw primary
        return try {
            rssVideoIds(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Both failed: report the API's error, which says more than the feed's.
            primary.addSuppressed(e)
            throw primary
        }
    }

    private companion object {
        const val CANDIDATES = 5
    }
}

internal fun VideoItem.toLiveVideo(): LiveVideo {
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

internal fun parseTime(value: String?): Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
