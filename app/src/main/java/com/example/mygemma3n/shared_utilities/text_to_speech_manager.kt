package com.example.mygemma3n.shared_utilities

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context
) : TextToSpeech.OnInitListener, DefaultLifecycleObserver {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady: Boolean = false

    init {
        // Observe process lifecycle to handle cleanup
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            tts?.language = Locale.US
        } else {
            Timber.e("TextToSpeech initialization failed: $status")
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Stop TTS when app goes to background
        tts?.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Cleanup TTS resources when process is destroyed
        shutdown()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}