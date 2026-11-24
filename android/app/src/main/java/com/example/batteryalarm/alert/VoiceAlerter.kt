package com.example.batteryalarm.alert

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import java.util.Locale

class VoiceAlerter(context: Context) : OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingMessage: String? = null
    private var pendingVoiceName: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return

        runCatching { tts.language = Locale.getDefault() }
        pendingVoiceName?.let { name ->
            pendingVoiceName = null
            applyVoice(name)
        }
        pendingMessage?.let { message ->
            pendingMessage = null
            speakPrepared(message)
        }
    }

    fun applyVoice(voiceName: String) {
        if (voiceName.isBlank()) return
        if (!ready) {
            pendingVoiceName = voiceName
            return
        }

        val voice = runCatching { tts.voices?.firstOrNull { it.name == voiceName } }.getOrNull()
        if (voice != null) {
            runCatching { tts.voice = voice }
        }
    }

    fun speak(message: String) {
        if (!ready) {
            pendingMessage = message
            return
        }
        speakPrepared(message)
    }

    private fun speakPrepared(message: String) {
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    fun stop() {
        pendingMessage = null
        if (ready) {
            runCatching { tts.stop() }
        }
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }

    private companion object {
        const val UTTERANCE_ID = "battery-alarm-low-battery"
    }
}
