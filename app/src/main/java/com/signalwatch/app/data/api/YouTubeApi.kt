package com.signalwatch.app.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/** YouTube Data API v3. Quota costs per call are in [com.signalwatch.app.data.quota.QuotaTracker]. */
interface YouTubeApi {

    @GET("channels")
    suspend fun channelsByHandle(
        @Query("forHandle") handle: String,
        @Query("part") part: String = "snippet,contentDetails",
    ): ChannelListResponse

    @GET("channels")
    suspend fun channelsById(
        @Query("id") id: String,
        @Query("part") part: String = "snippet",
    ): ChannelListResponse

    /** Newest first. Used on the uploads playlist for the live check. */
    @GET("playlistItems")
    suspend fun playlistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "contentDetails",
        @Query("maxResults") maxResults: Int = 5,
    ): PlaylistItemListResponse

    @GET("videos")
    suspend fun videos(
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,liveStreamingDetails",
    ): VideoListResponse

    @GET("liveChat/messages")
    suspend fun liveChatMessages(
        @Query("liveChatId") liveChatId: String,
        @Query("pageToken") pageToken: String?,
        @Query("part") part: String = "snippet,authorDetails",
        @Query("maxResults") maxResults: Int = 200,
    ): LiveChatMessageListResponse
}
