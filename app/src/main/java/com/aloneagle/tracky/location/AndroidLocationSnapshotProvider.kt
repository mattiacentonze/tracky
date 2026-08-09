package com.aloneagle.tracky.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.domain.model.LocationSnapshot
import com.aloneagle.tracky.domain.service.LocationSnapshotProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidLocationSnapshotProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationSnapshotProvider {
    private val snapshotMutex = Mutex()
    @Volatile private var cachedSnapshot: LocationSnapshot? = null

    override suspend fun currentSnapshot(): LocationSnapshot? {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) return null
        cachedSnapshot.takeIfFresh()?.let { return it }
        return snapshotMutex.withLock {
            cachedSnapshot.takeIfFresh()?.let { return@withLock it }
            val client = LocationServices.getFusedLocationProviderClient(context)
            val currentLocation = try {
                val tokenSource = CancellationTokenSource()
                try {
                    withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                        val stillHasLocationPermission =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                        if (!stillHasLocationPermission) return@withTimeoutOrNull null
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token).await()
                    }
                } finally {
                    tokenSource.cancel()
                }
            } catch (_: SecurityException) {
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val location = currentLocation ?: try {
                withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                    val stillHasLocationPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                    if (!stillHasLocationPermission) return@withTimeoutOrNull null
                    client.lastLocation.await()
                }
            } catch (_: SecurityException) {
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            location?.takeIf { candidate ->
                val age = System.currentTimeMillis() - candidate.time
                age in 0..MAX_LOCATION_AGE_MILLIS && candidate.accuracy <= MAX_LOCATION_ACCURACY_METERS
            }?.let {
                LocationSnapshot(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy,
                    observedAt = it.time.takeIf { time -> time > 0 } ?: System.currentTimeMillis(),
                )
            }?.also { snapshot -> cachedSnapshot = snapshot }
        }
    }

    private fun LocationSnapshot?.takeIfFresh(): LocationSnapshot? {
        val snapshot = this ?: return null
        val age = System.currentTimeMillis() - snapshot.observedAt
        return snapshot.takeIf { age in 0..LOCATION_CACHE_MILLIS }
    }
}

private const val LOCATION_CACHE_MILLIS = 60_000L
private const val LOCATION_TIMEOUT_MILLIS = 3_000L
private const val MAX_LOCATION_AGE_MILLIS = 5 * 60_000L
private const val MAX_LOCATION_ACCURACY_METERS = 250f
