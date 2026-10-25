package com.signalwatch.app.data.repo

import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.api.ChannelListResponse
import com.signalwatch.app.data.api.LiveChatMessageListResponse
import com.signalwatch.app.data.api.LiveStreamingDetails
import com.signalwatch.app.data.api.PlaylistItem
import com.signalwatch.app.data.api.PlaylistItemContentDetails
import com.signalwatch.app.data.api.PlaylistItemListResponse
import com.signalwatch.app.data.api.VideoItem
import com.signalwatch.app.data.api.VideoListResponse
import com.signalwatch.app.data.api.VideoSnippet
import com.signalwatch.app.data.api.YouTubeApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class LiveCheckerTest {

    private val channelId = "UCabcdefghijklmnopqrstuv"
    private val uploadsId = "UUabcdefghijklmnopqrstuv"

    /** Mocked Data API: canned playlist and video responses, or an error to throw. */
    private class FakeApi : YouTubeApi {
        var playlistIds: List<String> = emptyList()
        var playlistError: Exception? = null
        val videos = mutableMapOf<String, VideoItem>()
        val playlistCalls = mutableListOf<String>()
        val videoCalls = mutableListOf<String>()

        override suspend fun playlistItems(playlistId: String, part: String, maxResults: Int): PlaylistItemListResponse {
            playlistCalls += playlistId
            playlistError?.let { throw it }
            return PlaylistItemListResponse(
                playlistIds.take(maxResults).map { PlaylistItem(PlaylistItemContentDetails(it)) },
            )
        }

        override suspend fun videos(ids: String, part: String): VideoListResponse {
            videoCalls += ids
            return VideoListResponse(ids.split(",").mapNotNull { videos[it] })
        }

        override suspend fun channelsByHandle(handle: String, part: String): ChannelListResponse = error("unused")
        override suspend fun channelsById(id: String, part: String): ChannelListResponse = error("unused")
        override suspend fun liveChatMessages(
            liveChatId: String, pageToken: String?, part: String, maxResults: Int,
        ): LiveChatMessageListResponse = error("unused")
    }

    private class CountingCaller : ApiCaller {
        val costs = mutableListOf<Int>()
        override suspend fun <T> call(cost: Int, block: suspend () -> T): T {
            costs += cost
            return block()
        }
    }

    private val api = FakeApi()
    private val caller = CountingCaller()
    private var rssIds: List<String>? = null
    private var rssCalls = 0
    private val checker = LiveChecker(api, caller) {
        rssCalls++
        rssIds ?: throw IOException("Channel feed returned HTTP 404")
    }

    private fun video(id: String, state: String, started: Boolean = state == "live") = VideoItem(
        id = id,
        snippet = VideoSnippet(title = "Video $id", liveBroadcastContent = state),
        liveStreamingDetails = if (state == "none") null else LiveStreamingDetails(
            actualStartTime = if (started) "2026-10-25T14:00:00Z" else null,
            scheduledStartTime = "2026-10-25T14:00:00Z",
            activeLiveChatId = if (started) "chat-$id" else null,
        ),
    )

    private fun check() = runBlocking { checker.findLiveVideo(channelId, uploadsId) }

    @Test
    fun liveVideoInUploadsIsFound() {
        api.playlistIds = listOf("old1", "live1", "old2")
        api.videos["old1"] = video("old1", "none")
        api.videos["live1"] = video("live1", "live")
        api.videos["old2"] = video("old2", "none")

        val live = check()

        assertEquals("live1", live?.videoId)
        assertEquals("chat-live1", live?.liveChatId)
        assertEquals(listOf(uploadsId), api.playlistCalls)
        assertEquals(listOf("old1,live1,old2"), api.videoCalls)
        assertEquals("playlistItems + videos = 2 units", listOf(1, 1), caller.costs)
        assertEquals("RSS isn't touched when the API works", 0, rssCalls)
    }

    @Test
    fun upcomingBroadcastIsNotLive() {
        api.playlistIds = listOf("soon")
        api.videos["soon"] = video("soon", "upcoming")

        assertNull(check())
        // Upcoming streams can still go live, so they're asked about again next time.
        assertNull(check())
        assertEquals(listOf("soon", "soon"), api.videoCalls)
    }

    @Test
    fun noLiveVideoReturnsNullAndCachesFinishedOnes() {
        api.playlistIds = listOf("a", "b")
        api.videos["a"] = video("a", "none")
        api.videos["b"] = video("b", "none")

        assertNull(check())
        assertNull(check())

        assertEquals("finished videos aren't asked about twice", listOf("a,b"), api.videoCalls)
        assertEquals("second check costs only the playlist call", listOf(1, 1, 1), caller.costs)
    }

    @Test
    fun onlyTheFiveNewestUploadsAreChecked() {
        api.playlistIds = (1..8).map { "v$it" }
        api.playlistIds.forEach { api.videos[it] = video(it, "none") }

        check()

        assertEquals(listOf("v1,v2,v3,v4,v5"), api.videoCalls)
    }

    @Test
    fun playlistErrorFallsBackToRss() {
        api.playlistError = IOException("timeout")
        rssIds = listOf("live1")
        api.videos["live1"] = video("live1", "live")

        assertEquals("live1", check()?.videoId)
        assertEquals(1, rssCalls)
    }

    @Test
    fun quotaExceededSkipsRssAndReportsQuota() {
        val quota = ApiException(403, "quotaExceeded", "The request cannot be completed because you have exceeded your quota.")
        api.playlistError = quota
        rssIds = listOf("x")

        try {
            check()
            fail("expected the quota error")
        } catch (e: ApiException) {
            assertSame(quota, e)
            assertTrue(e.isQuotaExceeded)
        }
        assertEquals("RSS can't give live status without videos.list", 0, rssCalls)
        assertTrue(api.videoCalls.isEmpty())
    }

    @Test
    fun bothFailingReportsTheApiError() {
        val apiError = ApiException(500, "backendError", "Backend Error")
        api.playlistError = apiError
        rssIds = null // feed answers 404

        try {
            check()
            fail("expected an error")
        } catch (e: ApiException) {
            assertSame(apiError, e)
            assertEquals(1, e.suppressed.size)
        }
        assertEquals(1, rssCalls)
        assertTrue(api.videoCalls.isEmpty())
    }

    @Test
    fun emptyUploadsMeansOfflineWithoutVideosCall() {
        api.playlistIds = emptyList()

        assertNull(check())
        assertTrue(api.videoCalls.isEmpty())
        assertEquals(listOf(1), caller.costs)
    }
}
