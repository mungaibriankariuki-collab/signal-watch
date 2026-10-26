package com.signalwatch.app.data.api

import kotlinx.serialization.Serializable

@Serializable
data class ChannelListResponse(val items: List<ChannelItem> = emptyList())

@Serializable
data class ChannelItem(
    val id: String,
    val snippet: ChannelSnippet? = null,
    val contentDetails: ChannelContentDetails? = null,
)

@Serializable
data class ChannelContentDetails(val relatedPlaylists: RelatedPlaylists? = null)

@Serializable
data class RelatedPlaylists(
    /** The channel's uploads playlist ("UU..."); live and upcoming broadcasts appear in it too. */
    val uploads: String? = null,
)

@Serializable
data class PlaylistItemListResponse(val items: List<PlaylistItem> = emptyList())

@Serializable
data class PlaylistItem(val contentDetails: PlaylistItemContentDetails? = null)

@Serializable
data class PlaylistItemContentDetails(val videoId: String? = null)

@Serializable
data class ChannelSnippet(
    val title: String = "",
    val customUrl: String? = null,
    val thumbnails: Thumbnails? = null,
)

@Serializable
data class Thumbnails(
    val default: Thumbnail? = null,
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
) {
    fun best(): String? = (high ?: medium ?: default)?.url
}

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class VideoListResponse(val items: List<VideoItem> = emptyList())

@Serializable
data class VideoItem(
    val id: String,
    val snippet: VideoSnippet? = null,
    val liveStreamingDetails: LiveStreamingDetails? = null,
    val status: VideoStatus? = null,
    val contentDetails: VideoContentDetails? = null,
)

@Serializable
data class VideoStatus(val embeddable: Boolean? = null)

@Serializable
data class VideoContentDetails(val regionRestriction: RegionRestriction? = null)

/** Country codes; YouTube sends either an allow list or a block list. */
@Serializable
data class RegionRestriction(
    val allowed: List<String>? = null,
    val blocked: List<String>? = null,
)

@Serializable
data class VideoSnippet(
    val title: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    /** "live", "upcoming" or "none". */
    val liveBroadcastContent: String = "none",
)

@Serializable
data class LiveStreamingDetails(
    val actualStartTime: String? = null,
    val actualEndTime: String? = null,
    val scheduledStartTime: String? = null,
    val activeLiveChatId: String? = null,
)

@Serializable
data class LiveChatMessageListResponse(
    val nextPageToken: String? = null,
    val pollingIntervalMillis: Long = 0,
    val offlineAt: String? = null,
    val items: List<LiveChatMessage> = emptyList(),
)

@Serializable
data class LiveChatMessage(
    val id: String,
    val snippet: LiveChatSnippet? = null,
    val authorDetails: AuthorDetails? = null,
)

@Serializable
data class LiveChatSnippet(
    val type: String = "",
    val publishedAt: String? = null,
    val displayMessage: String? = null,
    val textMessageDetails: TextMessageDetails? = null,
)

@Serializable
data class TextMessageDetails(val messageText: String? = null)

@Serializable
data class AuthorDetails(
    val channelId: String = "",
    val displayName: String = "",
    val profileImageUrl: String? = null,
    val isChatOwner: Boolean = false,
    val isChatModerator: Boolean = false,
)

@Serializable
data class ApiErrorEnvelope(val error: ApiError? = null)

@Serializable
data class ApiError(
    val code: Int = 0,
    val message: String = "",
    val errors: List<ApiErrorDetail> = emptyList(),
)

@Serializable
data class ApiErrorDetail(val reason: String = "", val message: String = "")
