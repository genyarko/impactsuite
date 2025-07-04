package com.example.mygemma3n.models

//import android.content.Context
//import dagger.hilt.android.qualifiers.ApplicationContext
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import org.tensorflow.lite.DataType
//import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import javax.inject.Inject
//import javax.inject.Singleton
//import kotlin.math.sqrt
//
///**
// * Lazy-loaded singleton around **average_word_embedder.tflite** (1×256 INT32 → 512-D float).
// *
// * Usage:
// * ```kotlin
// * val vec = averageWordHelper.embed("This is a test")  // 512-dim float[]
// * ```
// */
//@Singleton
//class AverageWordEmbedderHelper @Inject constructor(
//    @ApplicationContext private val context: Context
//) {
//
//    companion object {
//        private const val MAX_LEN = 256         // model expects 1×256 tokens
//        private const val EMBEDDING_DIM = 512   // output size
//        private const val PAD_ID = 0
//        private const val UNK_ID = 1
//    }
//
//    private val mutex = Mutex()
//    private var model: AverageWordEmbedder? = null
//
//    /** Ensure interpreter is loaded exactly once (thread-safe). */
//    private suspend fun ensureLoaded() = mutex.withLock {
//        if (model == null) {
//            model = AverageWordEmbedder.newInstance(context) // looks in assets/models/
//        }
//    }
//
//    /**
//     * @return 512-dimensional L2-normalised embedding for *text*.
//     */
//    suspend fun embed(text: String): FloatArray {
//        ensureLoaded()
//
//        /* ── 1. Toy whitespace tokeniser → Int IDs ── */
//        val ids = IntArray(MAX_LEN) { PAD_ID }
//        val words = text.trim().split(Regex("\\s+")).take(MAX_LEN)
//        words.forEachIndexed { i, w -> ids[i] = hashWord(w) }
//
//        /* ── 2. Wrap IDs in TensorBuffer ── */
//        val input = TensorBuffer.createFixedSize(intArrayOf(1, MAX_LEN), DataType.INT32)
//        input.loadBuffer(intArrayToBb(ids))
//
//        /* ── 3. Inference ── */
//        val out = model!!
//            .process(input)
//            .statefulPartitionedCall0AsTensorBuffer
//            .floatArray                                  // len = 512
//
//        /* ── 4. L2 normalise ── */
//        var norm = 0f
//        out.forEach { v -> norm += v * v }
//        if (norm > 0f) {
//            val inv = 1f / sqrt(norm)
//            for (i in out.indices) out[i] *= inv
//        }
//        return out
//    }
//
//    /** Simple hash-to-ID (replace with real vocab if available). */
//    private fun hashWord(word: String): Int {
//        val h = word.lowercase().hashCode()
//        return 2 + (h and 0x7FFFFFFF) % 50_000   // avoid PAD/UNK
//    }
//
//    private fun intArrayToBb(arr: IntArray): ByteBuffer =
//        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).apply {
//            arr.forEach { putInt(it) }
//            rewind()
//        }
//
//    /** Call to release JNI resources (e.g. in ViewModel.onCleared()). */
//    suspend fun close() = mutex.withLock {
//        model?.close()
//        model = null
//    }
//}
