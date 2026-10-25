package com.signalwatch.app.ui.live

import android.view.View
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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * The official IFrame embed (android-youtube-player). Playback only: nothing is downloaded or
 * recorded. The activity handles config changes itself, so rotating keeps this same view and
 * the stream doesn't restart.
 */
@Composable
fun YouTubePlayer(
    videoId: String,
    onEnterFullscreen: (fullscreenView: View, exit: () -> Unit) -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enter by rememberUpdatedState(onEnterFullscreen)
    val exit by rememberUpdatedState(onExitFullscreen)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }

    val view = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) =
                    enter(fullscreenView, exitFullscreen)

                override fun onExitFullscreen() = exit()
            })
            val options = IFramePlayerOptions.Builder()
                .controls(1)
                .fullscreen(1)
                .rel(0)
                .ivLoadPolicy(3)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
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
