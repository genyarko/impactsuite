package com.mygemma3n.aiapp.feature.analytics

import androidx.room.TypeConverter

class AnalyticsTypeConverters {

    @TypeConverter
    fun fromInteractionType(value: InteractionType): String {
        return value.name
    }

    @TypeConverter
    fun toInteractionType(value: String): InteractionType {
        return InteractionType.valueOf(value)
    }

    @TypeConverter
    fun fromMasteryLevel(value: MasteryLevel): String {
        return value.name
    }

    @TypeConverter
    fun toMasteryLevel(value: String): MasteryLevel {
        return MasteryLevel.valueOf(value)
    }

    @TypeConverter
    fun fromGapType(value: GapType): String {
        return value.name
    }

    @TypeConverter
    fun toGapType(value: String): GapType {
        return GapType.valueOf(value)
    }

    @TypeConverter
    fun fromGapPriority(value: GapPriority): String {
        return value.name
    }

    @TypeConverter
    fun toGapPriority(value: String): GapPriority {
        return GapPriority.valueOf(value)
    }

    @TypeConverter
    fun fromRecommendationType(value: RecommendationType): String {
        return value.name
    }

    @TypeConverter
    fun toRecommendationType(value: String): RecommendationType {
        return RecommendationType.valueOf(value)
    }

    @TypeConverter
    fun fromTrendDirection(value: TrendDirection): String {
        return value.name
    }

    @TypeConverter
    fun toTrendDirection(value: String): TrendDirection {
        return TrendDirection.valueOf(value)
    }
}