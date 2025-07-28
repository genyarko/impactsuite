package com.example.mygemma3n.remote

// feature/tutor/data/TutorDatabase.kt


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mygemma3n.data.ExplanationDepth
import com.example.mygemma3n.data.LearningPace
import com.example.mygemma3n.data.LearningStyle
import com.example.mygemma3n.data.TutorDao
import com.example.mygemma3n.data.TutorSessionType
import com.example.mygemma3n.feature.quiz.Subject
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.data.ConceptMasteryEntity
import com.example.mygemma3n.data.LearningPreferenceEntity
import com.example.mygemma3n.data.StudentProfileEntity
import com.example.mygemma3n.data.TutorSessionEntity

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