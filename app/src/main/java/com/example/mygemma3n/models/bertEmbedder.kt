package com.example.mygemma3n.models

// File: app/src/main/java/com/example/mygemma3n/embedder/BertEmbedderHelper.kt

//import android.content.Context
//import com.example.mygemma3n.ml.BertEmbedder          // generated TFLite wrapper
//import org.tensorflow.lite.DataType
//import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
//import dagger.hilt.android.qualifiers.ApplicationContext
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import javax.inject.Inject
//import javax.inject.Singleton
//import java.nio.ByteBuffer
//import kotlin.math.sqrt
//
///**
// * Singleton helper for running inferences on a Mobile-BERT (.tflite) model.
// * The model file must live in **assets/models/mobile_bert.tflite**.
// */
//@Singleton
//class BertEmbedderHelper @Inject constructor(
//    @ApplicationContext private val context: Context
//) {
//    private val mutex = Mutex()
//    private var embedder: BertEmbedder? = null
//
//    /** Lazily load the model interpreter if not already loaded. */
//    private suspend fun ensureLoaded() = mutex.withLock {
//        if (embedder == null) {
//            // newInstance() looks for assets/models/mobile_bert.tflite
//            embedder = BertEmbedder.newInstance(context)
//        }
//    }
//
//    /**
//     * Embed a tokenized input (ids, mask, segment IDs).
//     *
//     * @param ids      ByteBuffer of 1×128 int32 token IDs.
//     * @param masks    ByteBuffer of 1×128 int32 attention-mask bits.
//     * @param segments ByteBuffer of 1×128 int32 segment IDs.
//     * @return         L2-normalized 768-dim embedding vector.
//     */
//    suspend fun embed(
//        ids: ByteBuffer,
//        masks: ByteBuffer,
//        segments: ByteBuffer
//    ): FloatArray {
//        ensureLoaded()
//
//        // Prepare TFLite support buffers
//        val shape = intArrayOf(1, 128)
//        val idsT      = TensorBuffer.createFixedSize(shape, DataType.INT32)
//        val masksT    = TensorBuffer.createFixedSize(shape, DataType.INT32)
//        val segmentsT = TensorBuffer.createFixedSize(shape, DataType.INT32)
//
//        idsT.loadBuffer(ids)
//        masksT.loadBuffer(masks)
//        segmentsT.loadBuffer(segments)
//
//        // Run inference
//        val raw = embedder!!
//            .process(idsT, masksT, segmentsT)
//            .moduleApplyTokensbertpoolersqueezeAsTensorBuffer
//            .floatArray
//
//        // L2-normalize the output vector
//        var sum = 0f
//        raw.forEach { v -> sum += v * v }
//        if (sum > 0f) {
//            val invNorm = 1f / sqrt(sum)
//            for (i in raw.indices) {
//                raw[i] = raw[i] * invNorm
//            }
//        }
//        return raw
//    }
//
//    /** Release the interpreter and JNI resources when no longer needed. */
//    suspend fun close() = mutex.withLock {
//        embedder?.close()
//        embedder = null
//    }
//}
