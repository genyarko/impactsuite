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

    @Query("SELECT COUNT(*) FROM hospitals")
    suspend fun countAll(): Int
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


    @Query("SELECT * FROM hospitals")
    suspend fun getAllHospitals(): List<Hospital>

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
    version = 2,
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

        // Add this to your EmergencyDatabase.kt file, replacing the existing prepopulateEmergencyData method

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

            // Add hospitals for multiple cities in Ghana
            database.hospitalDao().insertHospitals(
                listOf(
                    // ACCRA HOSPITALS
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
                    ),
                    Hospital(
                        id = "ga_east",
                        name = "Ga East Municipal Hospital",
                        address = "Abokobi, Accra",
                        phone = "0302970344",
                        latitude = 5.7061,
                        longitude = -0.2464,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 100,
                        rating = 3.8f
                    ),
                    Hospital(
                        id = "lekma",
                        name = "LEKMA Hospital",
                        address = "Teshie, Accra",
                        phone = "0303314850",
                        latitude = 5.5837,
                        longitude = -0.1007,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 120,
                        rating = 3.9f
                    ),

                    // KUMASI HOSPITALS
                    Hospital(
                        id = "komfo_anokye",
                        name = "Komfo Anokye Teaching Hospital",
                        address = "Bantama Road, Kumasi",
                        phone = "0322022301",
                        latitude = 6.6971,
                        longitude = -1.6163,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 1200,
                        rating = 4.3f
                    ),
                    Hospital(
                        id = "manhyia",
                        name = "Manhyia Government Hospital",
                        address = "Manhyia, Kumasi",
                        phone = "0322033534",
                        latitude = 6.7047,
                        longitude = -1.6062,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 300,
                        rating = 3.9f
                    ),
                    Hospital(
                        id = "suntreso",
                        name = "Suntreso Government Hospital",
                        address = "Suntreso, Kumasi",
                        phone = "0322029462",
                        latitude = 6.6244,
                        longitude = -1.6521,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 150,
                        rating = 3.7f
                    ),
                    Hospital(
                        id = "knust",
                        name = "KNUST Hospital",
                        address = "University Campus, Kumasi",
                        phone = "0322060258",
                        latitude = 6.6745,
                        longitude = -1.5717,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 200,
                        rating = 4.1f
                    ),

                    // TAKORADI HOSPITALS
                    Hospital(
                        id = "effia_nkwanta",
                        name = "Effia Nkwanta Regional Hospital",
                        address = "Effiakuma, Takoradi",
                        phone = "0312021915",
                        latitude = 4.9256,
                        longitude = -1.7531,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 600,
                        rating = 4.0f
                    ),
                    Hospital(
                        id = "takoradi_hospital",
                        name = "Takoradi Government Hospital",
                        address = "Takoradi",
                        phone = "0312023467",
                        latitude = 4.8994,
                        longitude = -1.7601,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 250,
                        rating = 3.8f
                    ),

                    // CAPE COAST HOSPITALS
                    Hospital(
                        id = "cape_coast_teaching",
                        name = "Cape Coast Teaching Hospital",
                        address = "Abura, Cape Coast",
                        phone = "0332132067",
                        latitude = 5.1315,
                        longitude = -1.2795,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 400,
                        rating = 4.2f
                    ),
                    Hospital(
                        id = "cape_coast_metro",
                        name = "Cape Coast Metropolitan Hospital",
                        address = "Ankaful, Cape Coast",
                        phone = "0332091325",
                        latitude = 5.1467,
                        longitude = -1.2664,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 150,
                        rating = 3.7f
                    ),

                    // TAMALE HOSPITALS
                    Hospital(
                        id = "tamale_teaching",
                        name = "Tamale Teaching Hospital",
                        address = "Tamale",
                        phone = "0372022454",
                        latitude = 9.4034,
                        longitude = -0.8424,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 800,
                        rating = 4.1f
                    ),
                    Hospital(
                        id = "tamale_central",
                        name = "Tamale Central Hospital",
                        address = "Tamale",
                        phone = "0372027928",
                        latitude = 9.4075,
                        longitude = -0.8533,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 200,
                        rating = 3.8f
                    ),

                    // HO HOSPITALS
                    Hospital(
                        id = "ho_teaching",
                        name = "Ho Teaching Hospital",
                        address = "Ho",
                        phone = "0362027570",
                        latitude = 6.6083,
                        longitude = 0.4713,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 320,
                        rating = 4.0f
                    ),
                    Hospital(
                        id = "volta_regional",
                        name = "Volta Regional Hospital",
                        address = "Ho",
                        phone = "0362024246",
                        latitude = 6.6121,
                        longitude = 0.4698,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 250,
                        rating = 3.9f
                    ),

                    // KOFORIDUA HOSPITALS
                    Hospital(
                        id = "eastern_regional",
                        name = "Eastern Regional Hospital",
                        address = "Koforidua",
                        phone = "0342020401",
                        latitude = 6.0836,
                        longitude = -0.2579,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 400,
                        rating = 3.9f
                    ),
                    Hospital(
                        id = "st_josephs",
                        name = "St. Joseph's Hospital",
                        address = "Koforidua",
                        phone = "0342023324",
                        latitude = 6.0874,
                        longitude = -0.2621,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 180,
                        rating = 4.1f
                    ),

                    // TEMA HOSPITALS
                    Hospital(
                        id = "tema_general",
                        name = "Tema General Hospital",
                        address = "Community 9, Tema",
                        phone = "0303202097",
                        latitude = 5.6698,
                        longitude = -0.0167,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 350,
                        rating = 3.9f
                    ),
                    Hospital(
                        id = "tema_polyclinic",
                        name = "Tema Polyclinic",
                        address = "Community 1, Tema",
                        phone = "0303204170",
                        latitude = 5.6173,
                        longitude = -0.0075,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 100,
                        rating = 3.7f
                    ),

                    // SUNYANI HOSPITALS
                    Hospital(
                        id = "sunyani_regional",
                        name = "Sunyani Regional Hospital",
                        address = "Sunyani",
                        phone = "0352027131",
                        latitude = 7.3392,
                        longitude = -2.3267,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 300,
                        rating = 3.8f
                    ),

                    // BOLGATANGA HOSPITALS
                    Hospital(
                        id = "upper_east_regional",
                        name = "Upper East Regional Hospital",
                        address = "Bolgatanga",
                        phone = "0382022218",
                        latitude = 10.7854,
                        longitude = -0.8521,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 250,
                        rating = 3.7f
                    ),

                    // WA HOSPITALS
                    Hospital(
                        id = "upper_west_regional",
                        name = "Upper West Regional Hospital",
                        address = "Wa",
                        phone = "0392022333",
                        latitude = 10.0601,
                        longitude = -2.5099,
                        specialization = "general",
                        hasEmergency = true,
                        beds = 200,
                        rating = 3.6f
                    )
                )
            )

            // Add first aid instructions (keeping existing code)
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