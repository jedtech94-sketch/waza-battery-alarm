package com.example.batteryalarm.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceCatalog(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val _voices = MutableStateFlow<List<String>>(emptyList())
    val voices: StateFlow<List<String>> = _voices

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        _voices.value = runCatching {
            tts.voices?.map { it.name }?.sorted().orEmpty()
        }.getOrDefault(emptyList())
    }

    fun close() {
        runCatching { tts.shutdown() }
    }
}
