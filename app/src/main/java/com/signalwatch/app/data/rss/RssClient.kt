package com.signalwatch.app.data.rss

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The channel's public uploads feed. Free: it doesn't touch the API quota. Newest first. */
@Singleton
class RssClient @Inject constructor(private val client: OkHttpClient) {

    suspend fun latestVideoIds(channelId: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        Log.d(TAG, "GET $url")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FeedHttpException(response.code)
            val body = response.body ?: throw IOException("Empty channel feed")
            parseVideoIds(body.byteStream())
        }
    }

    private fun parseVideoIds(stream: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val ids = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "yt:videoId") {
                ids += parser.nextText().trim()
            }
        }
        return ids
    }

    private companion object {
        const val TAG = "RssClient"
    }
}

/** The feed answered, but with an error status: YouTube's problem, not a lost connection. */
class FeedHttpException(val code: Int) : IOException("Channel feed returned HTTP $code")
