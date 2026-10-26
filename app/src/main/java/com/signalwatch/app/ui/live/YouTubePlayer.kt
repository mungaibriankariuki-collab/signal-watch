package com.signalwatch.app.ui.live

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.signalwatch.app.BuildConfig
import com.signalwatch.app.ui.MainViewModel

/**
 * The official IFrame embed (android-youtube-player). Playback only: nothing is downloaded or
 * recorded. The activity handles config changes itself, so rotating keeps this same view and
 * the stream doesn't restart.
 *
 * The library loads its player HTML with loadDataWithBaseURL. Since 13.0.0 the base URL and the
 * `origin` player parameter are https://<package name>, which is what YouTube now expects from
 * an app WebView; 12.x used https://www.youtube.com and got "Error 152 - 4". The page sends that
 * origin as the Referer (WebView's default policy, strict-origin-when-cross-origin).
 */
@Composable
fun YouTubePlayer(
    videoId: String,
    onEnterFullscreen: (fullscreenView: View, exit: () -> Unit) -> Unit,
    onExitFullscreen: () -> Unit,
    onError: (PlayerConstants.PlayerError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enter by rememberUpdatedState(onEnterFullscreen)
    val exit by rememberUpdatedState(onExitFullscreen)
    val fail by rememberUpdatedState(onError)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }

    val view = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            // The library already enables JavaScript and playback without a user gesture.
            findWebView(this)?.settings?.domStorageEnabled = true
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) =
                    enter(fullscreenView, exitFullscreen)

                override fun onExitFullscreen() = exit()
            })
            val origin = "https://${context.packageName}"
            Log.i(
                MainViewModel.PLAYER_TAG,
                "android-youtube-player ${BuildConfig.YT_PLAYER_VERSION}: loadDataWithBaseURL " +
                    "base URL $origin, origin $origin, Referer $origin/",
            )
            val options = IFramePlayerOptions.Builder(context)
                .origin(origin)
                .controls(1)
                .fullscreen(1)
                .rel(0)
                .ivLoadPolicy(3)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    Log.w(MainViewModel.PLAYER_TAG, "player error $error")
                    fail(error)
                }
            }, options)
        }
    }

    LaunchedEffect(player, videoId) {
        player?.loadVideo(videoId, 0f)
    }

    DisposableEffect(lifecycle, view) {
        lifecycle.addObserver(view)
        onDispose {
            lifecycle.removeObserver(view)
            view.release()
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

private fun findWebView(view: View): WebView? = when (view) {
    is WebView -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
    else -> null
}
