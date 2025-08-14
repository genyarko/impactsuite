package com.mygemma3n.aiapp.data.local.entities


import com.mygemma3n.aiapp.shared_utilities.OfflineRAG

fun SubjectEntity.toDomain(): Pair<OfflineRAG.Subject, Float> =
    OfflineRAG.Subject.valueOf(subject) to accuracy
