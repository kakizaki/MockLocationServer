package com.example.mocklocationserver.web.service

import android.location.Location
import com.example.mocklocationserver.web.data.InMemoryLocationRequestRepository
import com.example.mocklocationserver.web.dto.LocationRequest
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds


class MockLocationProvideService(
    repository: InMemoryLocationRequestRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val locationFlow = repository.location.transformLatest {
        emit(toLocation(it, true))
        if (it.repeatedlyUpdate) {
            while (true) {
                delay(1.seconds)
                emit(toLocation(it, false))
            }
        }
    }

    private fun toLocation(l: LocationRequest, isNewLocation: Boolean): Location {
        val currentTime = System.currentTimeMillis()
        val realtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()

        return Location("").apply {
            latitude = l.latitude
            longitude = l.longitude
            altitude = l.altitude
            accuracy = l.haccuracy.toFloat()
            speed = if (isNewLocation) l.velocity.toFloat() else 0f
            bearing = 0f
            time = currentTime
            elapsedRealtimeNanos = realtimeNanos
        }
    }

}




