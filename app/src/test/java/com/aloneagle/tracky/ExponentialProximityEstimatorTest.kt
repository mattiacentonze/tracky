package com.aloneagle.tracky

import com.aloneagle.tracky.data.repository.ExponentialProximityEstimator
import com.aloneagle.tracky.domain.model.ProximityEstimate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExponentialProximityEstimatorTest {
    private val estimator = ExponentialProximityEstimator()

    @Test
    fun smooth_usesExponentialAverage() {
        val smoothed = estimator.smooth(previous = -70.0, currentRssi = -62)

        assertThat(smoothed).isWithin(0.001).of(-68.0)
    }

    @Test
    fun smooth_boundsPlausibleRssiAndRejectsInvalidPositiveReading() {
        assertThat(estimator.smooth(previous = null, currentRssi = -200)).isEqualTo(-105.0)
        assertThat(estimator.smooth(previous = null, currentRssi = -5)).isEqualTo(-20.0)
        assertThat(estimator.smooth(previous = -72.0, currentRssi = 0)).isEqualTo(-72.0)
    }

    @Test
    fun smooth_heavilyAttenuatesSingleLargeOutlier() {
        val smoothed = estimator.smooth(previous = -70.0, currentRssi = -30)

        assertThat(smoothed).isWithin(0.001).of(-69.04)
    }

    @Test
    fun estimate_mapsStrongSignalToNearState() {
        val estimate = estimator.estimate(previousSmoothed = -60.0, smoothedRssi = -58.0)

        assertThat(estimate.level).isEqualTo(ProximityEstimate.Level.Near)
        assertThat(estimate.trend).isEqualTo(ProximityEstimate.Trend.Stable)
        assertThat(estimate.signalPercent).isGreaterThan(70)
        assertThat(estimate.estimatedDistanceMeters).isWithin(0.001).of(1.0)
    }

    @Test
    fun estimate_reportsTrendOnlyForMeaningfulFilteredChange() {
        val improving = estimator.estimate(previousSmoothed = -73.0, smoothedRssi = -70.0)
        val jitter = estimator.estimate(previousSmoothed = -70.0, smoothedRssi = -68.5)
        val untrustedJump = estimator.estimate(previousSmoothed = -80.0, smoothedRssi = -60.0)

        assertThat(improving.trend).isEqualTo(ProximityEstimate.Trend.Improving)
        assertThat(jitter.trend).isEqualTo(ProximityEstimate.Trend.Stable)
        assertThat(untrustedJump.trend).isEqualTo(ProximityEstimate.Trend.Stable)
        assertThat(untrustedJump.level).isEqualTo(ProximityEstimate.Level.Far)
        assertThat(untrustedJump.signalPercent).isLessThan(50)
        assertThat(untrustedJump.estimatedDistanceMeters).isNull()
    }

    @Test
    fun estimate_usesHysteresisNearLevelBoundary() {
        val estimate = estimator.estimate(previousSmoothed = -68.0, smoothedRssi = -66.2)

        assertThat(estimate.level).isEqualTo(ProximityEstimate.Level.Warm)
    }

    @Test
    fun estimate_mapsVeryWeakSignalToLostWithoutFakeDistance() {
        val estimate = estimator.estimate(previousSmoothed = -88.0, smoothedRssi = -94.0)

        assertThat(estimate.level).isEqualTo(ProximityEstimate.Level.Far)
        assertThat(estimate.trend).isEqualTo(ProximityEstimate.Trend.Stable)
        assertThat(estimate.estimatedDistanceMeters).isNull()

        val confirmedLost = estimator.estimate(previousSmoothed = -92.0, smoothedRssi = -94.0)

        assertThat(confirmedLost.level).isEqualTo(ProximityEstimate.Level.Lost)
        assertThat(confirmedLost.estimatedDistanceMeters).isNull()
    }

    @Test
    fun estimate_waitsForSecondReadingBeforePublishingDistance() {
        val estimate = estimator.estimate(previousSmoothed = null, smoothedRssi = -58.0)

        assertThat(estimate.level).isEqualTo(ProximityEstimate.Level.Near)
        assertThat(estimate.estimatedDistanceMeters).isNull()
        assertThat(estimate.descriptor).contains("needs more readings")
    }

    @Test
    fun estimate_handlesNonFiniteInputConservatively() {
        val estimate = estimator.estimate(previousSmoothed = Double.NaN, smoothedRssi = Double.NaN)

        assertThat(estimate.level).isEqualTo(ProximityEstimate.Level.Lost)
        assertThat(estimate.signalPercent).isEqualTo(0)
        assertThat(estimate.estimatedDistanceMeters).isNull()
        assertThat(estimate.descriptor).contains("needs more readings")
    }
}
