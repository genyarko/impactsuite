package com.mygemma3n.aiapp.data.local

import com.mygemma3n.aiapp.data.local.dao.SubjectDao
import com.mygemma3n.aiapp.data.local.entities.toDomain
import com.mygemma3n.aiapp.shared_utilities.OfflineRAG
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
