package com.example.mygemma3n.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.mygemma3n.data.local.entities.SubjectEntity

@Dao
interface SubjectDao {
    @Query("SELECT subject, accuracy FROM subjects")
    fun getSubjectAccuracies(): List<SubjectEntity>
}

