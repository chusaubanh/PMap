package com.pmap.vn.engine

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

class VietnameseTtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpokenTime = 0L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val viLocale = Locale("vi", "VN")
            val result = tts?.setLanguage(viLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback nếu máy chưa cài giọng Việt
                tts?.language = Locale.getDefault()
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.0f)

            // Cấu hình âm thanh to rõ cho loa ngoài hoặc tai nghe xe máy
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            isReady = true
        }
    }

    fun speak(text: String, minIntervalMs: Long = 7000L) {
        if (!isReady) return
        val now = System.currentTimeMillis()
        if (now - lastSpokenTime < minIntervalMs) return

        lastSpokenTime = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PMapAlert_${now}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
