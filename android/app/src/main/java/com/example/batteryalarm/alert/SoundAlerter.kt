package com.example.batteryalarm.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import com.example.batteryalarm.R

class SoundAlerter(private val context: Context) {

    private var player: MediaPlayer? = null

    init {
        setSound("built_in")
    }

    fun setSound(key: String) {
        release()
        player = runCatching { createPlayer(key) }.getOrNull()
    }

    private fun createPlayer(key: String): MediaPlayer? {
        if (key == "silent") return null

        val player = when (key) {
            "alarm" -> MediaPlayer.create(
                context.applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            )
            "notification" -> MediaPlayer.create(
                context.applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
            "ringtone" -> MediaPlayer.create(
                context.applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            )
            else -> MediaPlayer.create(context.applicationContext, R.raw.alarm_tone)
        }

        return player?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setVolume(1.0f, 1.0f)
        }
    }

    fun play() {
        val activePlayer = player ?: return
        runCatching {
            activePlayer.seekTo(0)
            activePlayer.start()
        }
    }

    fun stop() {
        val activePlayer = player ?: return
        runCatching {
            if (activePlayer.isPlaying) activePlayer.pause()
        }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }
}
