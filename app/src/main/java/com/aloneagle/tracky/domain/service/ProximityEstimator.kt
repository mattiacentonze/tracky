package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.ProximityEstimate

interface ProximityEstimator {
    fun smooth(previous: Double?, currentRssi: Int): Double
    fun estimate(previousSmoothed: Double?, smoothedRssi: Double): ProximityEstimate
}
