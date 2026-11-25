package com.touki.shortdrama

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Now playing"
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09090F.toInt())
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 18f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(20, 16, 20, 16)
        })
        val playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3500
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setRewindIncrementMs(10_000)
            setFastForwardIncrementMs(10_000)
            setShowPreviousButton(false)
            setShowNextButton(false)
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.playWhenReady = true
        }
    }
    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
