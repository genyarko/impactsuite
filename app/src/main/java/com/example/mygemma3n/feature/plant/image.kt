package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
import androidx.room.*
import com.example.mygemma3n.shared_utilities.stripFences
import com.google.gson.Gson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID


// Plant Scanner State
data class PlantScanState(
    val isAnalyzing: Boolean = false,
    val currentAnalysis: PlantAnalysis? = null,
    val scanHistory: List<PlantAnalysis> = emptyList(),
    val error: String? = null
)

// Plant Analysis Data
data class PlantAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val species: String = "",
    val confidence: Float = 0f,
    val disease: String? = null,
    val severity: String? = null,
    val recommendations: List<String> = emptyList(),
    val imageUri: String? = null,
    val additionalInfo: PlantInfo? = null
)


// Plant Database
@Entity(tableName = "plant_info")
data class PlantInfo(
    @PrimaryKey val scientificName: String,
    val commonNames: List<String>,
    val family: String,
    val nativeRegion: String,
    val wateringNeeds: String,
    val sunlightNeeds: String,
    val soilType: String,
    val growthRate: String,
    val maxHeight: String,
    val toxicity: String? = null,
    val companionPlants: List<String> = emptyList()
)

@Entity(tableName = "plant_diseases")
data class PlantDisease(
    @PrimaryKey val id: String,
    val name: String,
    val scientificName: String,
    val affectedPlants: List<String>,
    val symptoms: List<String>,
    val causes: List<String>,
    val treatments: List<String>,
    val preventiveMeasures: List<String>,
    val severity: String
)

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val species: String,
    val confidence: Float,
    val disease: String?,
    val severity: String?,
    val recommendations: String, // JSON string
    val imageUri: String?
)

// DAOs
@Dao
interface PlantInfoDao {
    @Query("SELECT * FROM plant_info WHERE scientificName = :name OR :name IN (commonNames)")
    suspend fun getPlantInfo(name: String): PlantInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlantInfo(info: PlantInfo)

    @Query("SELECT * FROM plant_info")
    suspend fun getAllPlants(): List<PlantInfo>
}

@Dao
interface PlantDiseaseDao {
    @Query("SELECT * FROM plant_diseases WHERE name = :disease")
    suspend fun getDiseaseInfo(disease: String): PlantDisease?

    @Query("SELECT * FROM plant_diseases WHERE :plant IN (affectedPlants)")
    suspend fun getDiseasesForPlant(plant: String): List<PlantDisease>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDisease(disease: PlantDisease)
}

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int = 20): Flow<List<ScanHistoryEntity>>

    @Query("DELETE FROM scan_history WHERE timestamp < :threshold")
    suspend fun deleteOldScans(threshold: Long)
}

// Type Converters
class PlantConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        gson.fromJson(value, Array<String>::class.java).toList()
}

// Database
@Database(
    entities = [
        PlantInfo::class,
        PlantDisease::class,
        ScanHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(PlantConverters::class)
abstract class PlantDatabase : RoomDatabase() {
    abstract fun plantInfoDao(): PlantInfoDao
    abstract fun diseaseDao(): PlantDiseaseDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    suspend fun getAdditionalInfo(species: String): PlantInfo? {
        return plantInfoDao().getPlantInfo(species)
    }

    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        fun prepopulateData(database: PlantDatabase) {
            // Add common plants
            kotlinx.coroutines.GlobalScope.launch {
                database.plantInfoDao().insertPlantInfo(
                    PlantInfo(
                        scientificName = "Solanum lycopersicum",
                        commonNames = listOf("Tomato", "Love Apple"),
                        family = "Solanaceae",
                        nativeRegion = "South America",
                        wateringNeeds = "Regular, keep soil moist",
                        sunlightNeeds = "Full sun (6-8 hours)",
                        soilType = "Well-draining, pH 6.0-6.8",
                        growthRate = "Fast",
                        maxHeight = "3-6 feet",
                        companionPlants = listOf("Basil", "Carrots", "Parsley")
                    )
                )

                // Add common diseases
                database.diseaseDao().insertDisease(
                    PlantDisease(
                        id = "early_blight",
                        name = "Early Blight",
                        scientificName = "Alternaria solani",
                        affectedPlants = listOf("Tomato", "Potato"),
                        symptoms = listOf(
                            "Dark spots with concentric rings on lower leaves",
                            "Yellowing around spots",
                            "Stem lesions"
                        ),
                        causes = listOf("Fungal infection", "High humidity", "Poor air circulation"),
                        treatments = listOf(
                            "Remove affected leaves",
                            "Apply fungicide",
                            "Improve air circulation"
                        ),
                        preventiveMeasures = listOf(
                            "Use resistant varieties",
                            "Rotate crops",
                            "Water at base of plants"
                        ),
                        severity = "moderate"
                    )
                )
            }
        }
    }
}

// Helper functions
fun preprocessImage(bitmap: Bitmap): ByteArray {
    // Resize to 224x224 for model input
    val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

    // Convert to byte array
    val pixels = IntArray(224 * 224)
    resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

    val bytes = ByteArray(224 * 224 * 3)
    var idx = 0

    for (pixel in pixels) {
        // Extract RGB and normalize
        bytes[idx++] = ((pixel shr 16 and 0xFF) - 128).toByte()
        bytes[idx++] = ((pixel shr 8 and 0xFF) - 128).toByte()
        bytes[idx++] = ((pixel and 0xFF) - 128).toByte()
    }

    return bytes
}

 fun parseAnalysisFromJson(raw: String, imageUri: String?): PlantAnalysis = try {
    val obj = JSONObject(stripFences(raw))
    val recs = obj.optJSONArray("recommendations")?.let { arr ->
        List(arr.length()) { i -> arr.getString(i) }
    } ?: emptyList()

    PlantAnalysis(
        id              = UUID.randomUUID().toString(),
        timestamp       = System.currentTimeMillis(),
        species         = obj.getString("species"),
        confidence      = obj.getDouble("confidence").toFloat(),
        disease         = obj.optString("disease", null),
        severity        = obj.optString("severity", null),
        recommendations = recs,
        imageUri        = imageUri,
        additionalInfo  = null  // parse nested JSON here if needed
    )
} catch (e: Exception) {
    Timber.e(e, "JSON parse error – fallback")
    PlantAnalysis(
        id              = UUID.randomUUID().toString(),
        timestamp       = System.currentTimeMillis(),
        species         = "",
        confidence      = 0f,
        disease         = null,
        severity        = null,
        recommendations = emptyList(),
        imageUri        = imageUri,
        additionalInfo  = null
    )
}

