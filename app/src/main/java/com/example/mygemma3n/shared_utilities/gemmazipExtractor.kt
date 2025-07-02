package com.example.mygemma3n.shared_utilities

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.util.zip.ZipInputStream

/**
 *  Unzips the 2-file split archive stored in assets/models/
 *  and returns the absolute path to the extracted .task file.
 *
 *  Idempotent → if the .task file already exists it just returns the path.
 *
 *  @throws IOException if the archive pieces are missing or corrupted.
 */
suspend fun Context.ensureGemmaTaskOnDisk(): String = withContext(Dispatchers.IO) {
    val target = File(cacheDir, "gemma-3n-E2B-it-int4.task")
    if (target.exists()) return@withContext target.absolutePath      // done already

    // ---- 1️⃣ open the two parts in the correct order ----
    val partNames = listOf(
        "models/gemma-3n-E2B-it-int4.z01",
        "models/gemma-3n-E2B-it-int4.zip"
    )

    val streams = partNames.map { assetName ->
        assets.open(assetName) ?: throw FileNotFoundException(assetName)
    }

    // ---- 2️⃣ concatenate → ZipInputStream ----
    SequenceInputStream(streams[0], streams[1]).use { joined ->
        ZipInputStream(BufferedInputStream(joined)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".task")) {
                    // ---- 3️⃣ copy the single entry out to cacheDir ----
                    target.outputStream().use { out ->
                        zip.copyTo(out, bufferSize = 1 shl 16)
                    }
                    Timber.i("Gemma model extracted to ${target.absolutePath}")
                    break
                }
                entry = zip.nextEntry
            }
        }
    }

    require(target.exists()) { "Failed to extract Gemma .task from split zip" }
    target.absolutePath
}
