package com.mygemma3n.aiapp.shared_utilities

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
        if (!isReady || text.isBlank()) return
        
        // Chunk large text to prevent memory issues
        val maxChunkSize = 4000 // TTS has limits on text length
        val chunks = if (text.length > maxChunkSize) {
            text.chunked(maxChunkSize)
        } else {
            listOf(text)
        }
        
        // Speak first chunk immediately, queue others
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            try {
                tts?.speak(chunk.trim(), queueMode, null, "${text.hashCode()}_$index")
            } catch (e: Exception) {
                Timber.e(e, "Error speaking text chunk $index")
                // Stop on error to prevent further issues
                stop()
                return
            }
        }
    }
    
    fun stop() {
        tts?.stop()
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