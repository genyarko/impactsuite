package com.example.mygemma3n.data.local.entities


import com.example.mygemma3n.shared_utilities.OfflineRAG

fun SubjectEntity.toDomain(): Pair<OfflineRAG.Subject, Float> =
    OfflineRAG.Subject.valueOf(subject) to accuracy
