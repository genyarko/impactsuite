package com.mygemma3n.aiapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val subject: String,  // stores enum name
    var accuracy: Float               // mutable so Room can set
)
