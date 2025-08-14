package com.mygemma3n.aiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.mygemma3n.aiapp.data.local.entities.SubjectEntity

@Dao
interface SubjectDao {
    @Query("SELECT subject, accuracy FROM subjects")
    fun getSubjectAccuracies(): List<SubjectEntity>
}

