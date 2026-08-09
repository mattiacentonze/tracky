package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.LocationSnapshot

interface LocationSnapshotProvider {
    suspend fun currentSnapshot(): LocationSnapshot?
}
