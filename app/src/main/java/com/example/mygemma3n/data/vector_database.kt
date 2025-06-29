package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.local.dao.SubjectDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import com.example.mygemma3n.data.local.entities.SubjectEntity


// Room Entity for vector storage
@Entity(tableName = "vector_documents")
@TypeConverters(Converters::class)
data class VectorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "embedding") val embedding: List<Float>,
    @ColumnInfo(name = "metadata") val metadata: Map<String, String>
)

// Type converters for Room
class Converters {
    @TypeConverter
    fun fromFloatList(value: List<Float>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.toFloat() }
        }
    }

    @Entity(tableName = "subjects")
    data class SubjectEntity(
        @PrimaryKey val subject: String,  // or use Int or UUID if needed
        var accuracy: Float
    )


    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String {
        return value.entries.joinToString(";") { "${it.key}:${it.value}" }
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        return if (value.isEmpty()) {
            emptyMap()
        } else {
            value.split(";").associate {
                val (key, v) = it.split(":")
                key to v
            }
        }
    }
}

// DAO for vector operations
@Dao
interface VectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: VectorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<VectorEntity>)

    @Query("SELECT * FROM vector_documents")
    suspend fun getAll(): List<VectorEntity>

    @Query("SELECT * FROM vector_documents WHERE id = :id")
    suspend fun getById(id: String): VectorEntity?

    @Query("SELECT * FROM vector_documents WHERE metadata LIKE :metadataPattern")
    suspend fun getByMetadataPattern(metadataPattern: String): List<VectorEntity>

    @Delete
    suspend fun delete(vector: VectorEntity)

    @Query("DELETE FROM vector_documents")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vector_documents")
    suspend fun getCount(): Int
}

// Room Database
@Database(
    entities = [VectorEntity::class, SubjectEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vectorDao(): VectorDao
    abstract fun subjectDao(): SubjectDao  // 🔍 Add this line
}

// Vector Database implementation
@Singleton
class VectorDatabase @Inject constructor(
    private val roomDatabase: AppDatabase
) {

    data class VectorDocument(
        val id: String = UUID.randomUUID().toString(),
        val content: String,
        val embedding: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VectorDocument

            if (id != other.id) return false
            if (content != other.content) return false
            if (!embedding.contentEquals(other.embedding)) return false
            if (metadata != other.metadata) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + embedding.contentHashCode()
            result = 31 * result + metadata.hashCode()
            return result
        }
    }

    data class SearchResult(
        val document: VectorDocument,
        val score: Float
    )

    suspend fun insert(document: VectorDocument) = withContext(Dispatchers.IO) {
        val entity = VectorEntity(
            id = document.id,
            content = document.content,
            embedding = document.embedding.toList(),
            metadata = document.metadata
        )
        roomDatabase.vectorDao().insert(entity)
    }

    suspend fun insertBatch(documents: List<VectorDocument>) = withContext(Dispatchers.IO) {
        val entities = documents.map { doc ->
            VectorEntity(
                id = doc.id,
                content = doc.content,
                embedding = doc.embedding.toList(),
                metadata = doc.metadata
            )
        }
        roomDatabase.vectorDao().insertAll(entities)
    }

    suspend fun search(
        embedding: FloatArray,
        k: Int = 5,
        filter: Map<String, String>? = null
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Get documents based on filter
        val allDocuments = if (filter != null && filter.isNotEmpty()) {
            // Build pattern for metadata search
            val pattern = filter.entries.joinToString("%") { "${it.key}:${it.value}" }
            roomDatabase.vectorDao().getByMetadataPattern("%$pattern%")
        } else {
            roomDatabase.vectorDao().getAll()
        }

        // Calculate cosine similarity for each document
        val scoredDocs = allDocuments.map { entity ->
            val similarity = cosineSimilarity(
                embedding,
                entity.embedding.toFloatArray()
            )

            val document = VectorDocument(
                id = entity.id,
                content = entity.content,
                embedding = entity.embedding.toFloatArray(),
                metadata = entity.metadata
            )

            SearchResult(document, similarity)
        }

        // Return top-k results sorted by similarity
        scoredDocs
            .sortedByDescending { it.score }
            .take(k)
    }

    suspend fun searchWithThreshold(
        embedding: FloatArray,
        threshold: Float = 0.7f,
        maxResults: Int = 10,
        filter: Map<String, String>? = null
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        search(embedding, maxResults, filter)
            .filter { it.score >= threshold }
    }

    suspend fun getById(id: String): VectorDocument? = withContext(Dispatchers.IO) {
        roomDatabase.vectorDao().getById(id)?.let { entity ->
            VectorDocument(
                id = entity.id,
                content = entity.content,
                embedding = entity.embedding.toFloatArray(),
                metadata = entity.metadata
            )
        }
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        roomDatabase.vectorDao().getById(id)?.let { entity ->
            roomDatabase.vectorDao().delete(entity)
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        roomDatabase.vectorDao().deleteAll()
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        roomDatabase.vectorDao().getCount()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    // HNSW index optimization for faster search
    suspend fun optimizeIndex() = withContext(Dispatchers.IO) {
        // In a production app, you might implement HNSW or other
        // approximate nearest neighbor algorithms here
        // For now, we'll just ensure the database is properly indexed

        // This is a placeholder - Room handles its own indexing
        // You could add custom indexing logic here if needed
    }

    // Batch similarity search for multiple queries
    suspend fun batchSearch(
        embeddings: List<FloatArray>,
        k: Int = 5,
        filter: Map<String, String>? = null
    ): List<List<SearchResult>> = withContext(Dispatchers.IO) {
        embeddings.map { embedding ->
            search(embedding, k, filter)
        }
    }

    // Update document (preserving embedding)
    suspend fun updateContent(
        id: String,
        newContent: String,
        newMetadata: Map<String, String>? = null
    ) = withContext(Dispatchers.IO) {
        roomDatabase.vectorDao().getById(id)?.let { entity ->
            val updated = entity.copy(
                content = newContent,
                metadata = newMetadata ?: entity.metadata
            )
            roomDatabase.vectorDao().insert(updated)
        }
    }

    companion object {
        const val DEFAULT_EMBEDDING_DIM = 768 // Gemma 3n embedding dimension

        // Database configuration
        fun create(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vector_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}