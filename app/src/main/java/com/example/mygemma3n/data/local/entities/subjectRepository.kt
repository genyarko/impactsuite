package com.example.mygemma3n.data.local

import com.example.mygemma3n.data.local.dao.SubjectDao
import com.example.mygemma3n.data.local.entities.toDomain
import com.example.mygemma3n.shared_utilities.OfflineRAG
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao
) {
    suspend fun getSubjectsWithAccuracy(): List<Pair<OfflineRAG.Subject, Float>> {
        return dao.getSubjectAccuracies()
            .map { it.toDomain() }
    }
}
