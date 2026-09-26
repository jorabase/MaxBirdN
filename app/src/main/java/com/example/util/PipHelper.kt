package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import com.example.R

object PipHelper {
    const val ACTION_PIP_REWIND_10 = "com.example.pip.ACTION_REWIND_10"
    const val ACTION_PIP_PLAY_PAUSE = "com.example.pip.ACTION_PLAY_PAUSE"
    const val ACTION_PIP_FORWARD_10 = "com.example.pip.ACTION_FORWARD_10"

    fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val actions = mutableListOf<RemoteAction>()

        try {
            // 1. 10s Rewind / Back button (১০ সেকেন্ড পেছনে)
            val rewindIntent = PendingIntent.getBroadcast(
                context,
                101,
                Intent(ACTION_PIP_REWIND_10).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val rewindIcon = Icon.createWithResource(context, R.drawable.ic_pip_replay_10)
            actions.add(
                RemoteAction(
                    rewindIcon,
                    "১০ সেকেন্ড পেছনে",
                    "১০ সেকেন্ড পেছনে যান",
                    rewindIntent
                )
            )

            // 2. Play / Pause button
            val playPauseIntent = PendingIntent.getBroadcast(
                context,
                102,
                Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIcon = if (isPlaying) {
                Icon.createWithResource(context, R.drawable.ic_pip_pause)
            } else {
                Icon.createWithResource(context, R.drawable.ic_pip_play)
            }
            val playPauseTitle = if (isPlaying) "পজ" else "প্লে"
            actions.add(
                RemoteAction(
                    playPauseIcon,
                    playPauseTitle,
                    playPauseTitle,
                    playPauseIntent
                )
            )

            // 3. 10s Fast Forward button (১০ সেকেন্ড সামনে)
            val forwardIntent = PendingIntent.getBroadcast(
                context,
                103,
                Intent(ACTION_PIP_FORWARD_10).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val forwardIcon = Icon.createWithResource(context, R.drawable.ic_pip_forward_10)
            actions.add(
                RemoteAction(
                    forwardIcon,
                    "১০ সেকেন্ড সামনে",
                    "১০ সেকেন্ড সামনে যান",
                    forwardIntent
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("PipHelper", "Error building PiP actions: ${e.message}", e)
        }

        return actions
    }

    fun updatePipActions(activity: Activity?, isPlaying: Boolean, aspectRatio: Rational = Rational(16, 9)) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val actions = buildPipActions(activity, isPlaying)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setActions(actions)
                .build()
            activity.setPictureInPictureParams(params)
        } catch (e: Exception) {
            android.util.Log.e("PipHelper", "Error updating PiP params: ${e.message}")
        }
    }

    fun enterPipMode(activity: Activity?, isPlaying: Boolean, aspectRatio: Rational = Rational(16, 9)) {
        if (activity == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val actions = buildPipActions(activity, isPlaying)
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .setActions(actions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(false)
                    builder.setSeamlessResizeEnabled(true)
                }
                activity.enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                android.util.Log.e("PipHelper", "Error entering PiP mode: ${e.message}")
            }
        }
    }
}

@Composable
fun SetupPipController(
    player: ExoPlayer?,
    isPlaying: Boolean,
    aspectRatio: Rational = Rational(16, 9),
    onPipEntered: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Update PiP actions when isPlaying state changes while in PiP
    LaunchedEffect(isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true) {
            PipHelper.updatePipActions(activity, isPlaying, aspectRatio)
        }
    }

    DisposableEffect(player, activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    PipHelper.ACTION_PIP_REWIND_10 -> {
                        player?.let { p ->
                            val currentPos = p.currentPosition
                            val targetPos = (currentPos - 10000L).coerceAtLeast(0L)
                            p.seekTo(targetPos)
                        }
                    }
                    PipHelper.ACTION_PIP_PLAY_PAUSE -> {
                        player?.let { p ->
                            if (p.isPlaying) {
                                p.pause()
                            } else {
                                p.play()
                            }
                            if (activity != null) {
                                PipHelper.updatePipActions(activity, p.isPlaying, aspectRatio)
                            }
                        }
                    }
                    PipHelper.ACTION_PIP_FORWARD_10 -> {
                        player?.let { p ->
                            val currentPos = p.currentPosition
                            val duration = p.duration.coerceAtLeast(0L)
                            val targetPos = if (duration > 0L) {
                                (currentPos + 10000L).coerceAtMost(duration)
                            } else {
                                currentPos + 10000L
                            }
                            p.seekTo(targetPos)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(PipHelper.ACTION_PIP_REWIND_10)
            addAction(PipHelper.ACTION_PIP_PLAY_PAUSE)
            addAction(PipHelper.ACTION_PIP_FORWARD_10)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
}
