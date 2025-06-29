package com.example.mygemma3n.remote



import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Singleton

// Entities
@Entity(tableName = "hospitals")
data class Hospital(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val specialization: String, // trauma, cardiac, stroke, burn, general
    val hasEmergency: Boolean = true,
    val beds: Int = 0,
    val rating: Float = 0f
) {
    @Ignore
    var distanceKm: Double = 0.0

    @Ignore
    var estimatedMinutes: Int = 0
}

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey val service: String, // police, fire, medical, poison_control, disaster
    val primaryNumber: String,
    val secondaryNumber: String? = null,
    val smsNumber: String? = null,
    val region: String = "default",
    val lastUpdated: Date = Date()
)

@Entity(tableName = "first_aid_instructions")
data class FirstAidInstruction(
    @PrimaryKey val condition: String, // cpr, bleeding, burns, choking, fracture, shock, poisoning
    val severity: String, // mild, moderate, severe
    val lastUpdated: Date = Date()
)

@Entity(tableName = "first_aid_steps")
data class FirstAidStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instructionCondition: String,
    val order: Int,
    val instruction: String,
    val duration: String? = null,
    val imageUrl: String? = null
)

@Entity(tableName = "first_aid_warnings")
data class FirstAidWarning(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instructionCondition: String,
    val warning: String,
    val priority: Int = 0
)

// DAOs
@Dao
interface HospitalDao {
    @Query("""
        SELECT * FROM hospitals 
        WHERE specialization = :specialization OR specialization = 'general'
        ORDER BY 
            ((:latitude - latitude) * (:latitude - latitude) + 
             (:longitude - longitude) * (:longitude - longitude))
        LIMIT 10
    """)
    suspend fun getNearbyHospitalsRaw(
        latitude: Double,
        longitude: Double,
        specialization: String
    ): List<Hospital>

    suspend fun getNearbyHospitals(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double,
        specialization: String
    ): List<Hospital> {
        val hospitals = getNearbyHospitalsRaw(latitude, longitude, specialization)

        return hospitals.map { hospital ->
            val distance = calculateDistance(
                latitude, longitude,
                hospital.latitude, hospital.longitude
            )
            hospital.apply {
                distanceKm = distance
                estimatedMinutes = (distance * 2.5).toInt() // Rough estimate: 24 km/h average
            }
        }.filter { it.distanceKm <= maxDistanceKm }
            .sortedBy { it.distanceKm }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHospital(hospital: Hospital)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHospitals(hospitals: List<Hospital>)

    @Query("DELETE FROM hospitals")
    suspend fun deleteAll()

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts WHERE region = :region OR region = 'default'")
    suspend fun getContactsForRegion(region: String = "default"): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts")
    suspend fun getEmergencyContacts(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE service = :service LIMIT 1")
    suspend fun getContactByService(service: String): EmergencyContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<EmergencyContact>)
}

@Dao
interface FirstAidDao {
    @Transaction
    @Query("SELECT * FROM first_aid_instructions WHERE condition = :condition LIMIT 1")
    suspend fun getInstructionWithSteps(condition: String): InstructionWithStepsAndWarnings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstruction(instruction: FirstAidInstruction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: FirstAidStep)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarning(warning: FirstAidWarning)

    @Query("DELETE FROM first_aid_instructions")
    suspend fun deleteAllInstructions()

    suspend fun getInstructions(condition: String): FirstAidData? {
        val data = getInstructionWithSteps(condition) ?: return null
        return FirstAidData(
            condition = data.instruction.condition,
            severity = data.instruction.severity,
            steps = data.steps.sortedBy { it.order },
            warnings = data.warnings.sortedByDescending { it.priority }.map { it.warning }
        )
    }
}

// Relations
data class InstructionWithStepsAndWarnings(
    @Embedded val instruction: FirstAidInstruction,
    @Relation(
        parentColumn = "condition",
        entityColumn = "instructionCondition"
    )
    val steps: List<FirstAidStep>,
    @Relation(
        parentColumn = "condition",
        entityColumn = "instructionCondition"
    )
    val warnings: List<FirstAidWarning>
)

// Data classes for app use
data class FirstAidData(
    val condition: String,
    val severity: String,
    val steps: List<FirstAidStep>,
    val warnings: List<String>
)

// Type Converters
class EmergencyConverters {
    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(timestamp: Long?): Date? = timestamp?.let { Date(it) }
}

// Database
@Database(
    entities = [
        Hospital::class,
        EmergencyContact::class,
        FirstAidInstruction::class,
        FirstAidStep::class,
        FirstAidWarning::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(EmergencyConverters::class)
abstract class EmergencyDatabase : RoomDatabase() {
    abstract fun hospitalDao(): HospitalDao
    abstract fun contactDao(): EmergencyContactDao
    abstract fun firstAidDao(): FirstAidDao

    companion object {
        @Volatile
        private var INSTANCE: EmergencyDatabase? = null

        fun getInstance(context: android.content.Context): EmergencyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        private fun buildDatabase(context: android.content.Context): EmergencyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EmergencyDatabase::class.java,
                "emergency_database"
            )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate with emergency data
                        kotlinx.coroutines.GlobalScope.launch {
                            getInstance(context).apply {
                                prepopulateEmergencyData(this)
                            }
                        }
                    }
                })
                .build()
        }

        private suspend fun prepopulateEmergencyData(database: EmergencyDatabase) {
            // Add default emergency contacts
            database.contactDao().insertContacts(
                listOf(
                    EmergencyContact(
                        service = "police",
                        primaryNumber = "191",
                        secondaryNumber = "18555",
                        region = "ghana"
                    ),
                    EmergencyContact(
                        service = "fire",
                        primaryNumber = "192",
                        secondaryNumber = "0302772446",
                        region = "ghana"
                    ),
                    EmergencyContact(
                        service = "medical",
                        primaryNumber = "193",
                        secondaryNumber = "0302773906",
                        region = "ghana"
                    ),
                    EmergencyContact(
                        service = "poison_control",
                        primaryNumber = "0302665065",
                        region = "ghana"
                    ),
                    EmergencyContact(
                        service = "disaster",
                        primaryNumber = "0302772446",
                        smsNumber = "1070",
                        region = "ghana"
                    )
                )
            )

            // Add sample hospitals for Accra
            database.hospitalDao().insertHospitals(
                listOf(
                    Hospital(
                        id = "korle_bu",
                        name = "Korle Bu Teaching Hospital",
                        address = "Guggisberg Ave, Accra",
                        phone = "0302674000",
                        latitude = 5.5365,
                        longitude = -0.2257,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 2000,
                        rating = 4.2f
                    ),
                    Hospital(
                        id = "ridge",
                        name = "Ridge Hospital",
                        address = "Castle Rd, Accra",
                        phone = "0302667812",
                        latitude = 5.5641,
                        longitude = -0.1969,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 420,
                        rating = 4.0f
                    ),
                    Hospital(
                        id = "37_military",
                        name = "37 Military Hospital",
                        address = "Liberation Rd, Accra",
                        phone = "0302779906",
                        latitude = 5.6202,
                        longitude = -0.1713,
                        specialization = "trauma",
                        hasEmergency = true,
                        beds = 600,
                        rating = 4.5f
                    )
                )
            )

            // Add first aid instructions
            val cprCondition = "cpr"
            database.firstAidDao().apply {
                insertInstruction(
                    FirstAidInstruction(
                        condition = cprCondition,
                        severity = "severe"
                    )
                )

                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 1,
                    instruction = "Ensure the scene is safe"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 2,
                    instruction = "Check for responsiveness - tap and shout"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 3,
                    instruction = "Call emergency services immediately"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 4,
                    instruction = "Place person on firm surface, tilt head back"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 5,
                    instruction = "Give 30 chest compressions - push hard and fast",
                    duration = "15-18 seconds"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 6,
                    instruction = "Give 2 rescue breaths",
                    duration = "2-3 seconds each"
                ))
                insertStep(FirstAidStep(
                    instructionCondition = cprCondition,
                    order = 7,
                    instruction = "Continue cycles of 30 compressions and 2 breaths"
                ))

                insertWarning(FirstAidWarning(
                    instructionCondition = cprCondition,
                    warning = "Do not stop CPR until help arrives or person starts breathing",
                    priority = 1
                ))
                insertWarning(FirstAidWarning(
                    instructionCondition = cprCondition,
                    warning = "Compressions should be at least 2 inches deep",
                    priority = 2
                ))
            }
        }
    }
}