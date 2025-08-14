package com.mygemma3n.aiapp.remote

// feature/tutor/data/TutorDatabase.kt


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.mygemma3n.aiapp.data.ExplanationDepth
import com.mygemma3n.aiapp.data.LearningPace
import com.mygemma3n.aiapp.data.LearningStyle
import com.mygemma3n.aiapp.data.TutorDao
import com.mygemma3n.aiapp.data.TutorSessionType
import com.mygemma3n.aiapp.feature.quiz.Subject
import com.mygemma3n.aiapp.feature.quiz.Difficulty
import com.mygemma3n.aiapp.data.ConceptMasteryEntity
import com.mygemma3n.aiapp.data.LearningPreferenceEntity
import com.mygemma3n.aiapp.data.StudentProfileEntity
import com.mygemma3n.aiapp.data.TutorSessionEntity

@Database(
    entities = [
        StudentProfileEntity::class,
        TutorSessionEntity::class,
        LearningPreferenceEntity::class,
        ConceptMasteryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(TutorConverters::class)
abstract class TutorDatabase : RoomDatabase() {
    abstract fun tutorDao(): TutorDao
}

// Type Converters for the Tutor Database
class TutorConverters {
    // Learning Style
    @TypeConverter
    fun fromLearningStyle(style: LearningStyle): String = style.name

    @TypeConverter
    fun toLearningStyle(value: String): LearningStyle = LearningStyle.valueOf(value)

    // Tutor Session Type
    @TypeConverter
    fun fromSessionType(type: TutorSessionType): String = type.name

    @TypeConverter
    fun toSessionType(value: String): TutorSessionType = TutorSessionType.valueOf(value)

    // Subject (reusing from quiz)
    @TypeConverter
    fun fromSubject(subject: Subject): String = subject.name

    @TypeConverter
    fun toSubject(value: String): Subject = Subject.valueOf(value)

    // Explanation Depth
    @TypeConverter
    fun fromExplanationDepth(depth: ExplanationDepth): String = depth.name

    @TypeConverter
    fun toExplanationDepth(value: String): ExplanationDepth = ExplanationDepth.valueOf(value)

    // Learning Pace
    @TypeConverter
    fun fromLearningPace(pace: LearningPace): String = pace.name

    @TypeConverter
    fun toLearningPace(value: String): LearningPace = LearningPace.valueOf(value)
}