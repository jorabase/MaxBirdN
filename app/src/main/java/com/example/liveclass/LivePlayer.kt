package com.example.liveclass

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun LivePlayer(
    masterUrl: String,
    modifier: Modifier = Modifier,
    onError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    // Initialize ExoPlayer for HLS Live Streaming with zero-buffering engine
    val exoPlayer = remember(masterUrl) {
        com.example.player.ShikhoPlayerManager.buildExoPlayer(
            context = context,
            classType = com.example.player.PlayerClassType.LIVE
        ).apply {
            val mediaSource = com.example.player.ShikhoPlayerManager.createMediaSource(
                url = masterUrl,
                isLive = true,
                classType = com.example.player.PlayerClassType.LIVE,
                context = context
            )
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onError?.invoke("ভিডিও প্লেয়ারে সমস্যা হয়েছে: ${error.message}")
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
