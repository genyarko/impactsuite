package com.example.mygemma3n.shared_utilities

import android.content.Context
import android.content.res.AssetFileDescriptor
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads a TFLite flatbuffer directly from assets/models/<modelName>.tflite
 * via zero-copy memory-mapping. Requires that you’ve added:
 *
 *   androidResources {
 *     noCompress += "tflite"
 *   }
 *
 * to your Gradle config so these files aren’t compressed in the APK.
 *
 * @param context     your application or activity context
 * @param modelName   the base name of the model file (without “.tflite”)
 * @return            a MappedByteBuffer ready to feed into Interpreter()
 */
fun loadTfliteAsset(context: Context, modelName: String): MappedByteBuffer {
    // e.g. modelName = "TF_LITE_EMBEDDER" → assets/models/TF_LITE_EMBEDDER.tflite
    val assetPath = "models/$modelName.tflite"
    val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
    FileInputStream(afd.fileDescriptor).channel.use { channel ->
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
    }
}
