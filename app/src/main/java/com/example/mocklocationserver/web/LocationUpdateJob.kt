package com.example.mocklocationserver.web

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.example.mocklocationserver.web.dto.FakeLocation
import com.example.mocklocationserver.web.dto.RequestFakeLocation
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import java.util.*


interface LocationUpdateJobCallback {
    fun setLocation(l: Location, isNewLocation: Boolean)
}



class LocationUpdateJob() {
//val callback: LocationUpdateJobCallback) {

    data class Result(val l: Location, val isNewLocation: Boolean)

    private val supervisorJob = SupervisorJob()

    private val scope =
        CoroutineScope(Dispatchers.Default + supervisorJob)

    private var job: Job? = null

    private val _state = MutableStateFlow<Result?>(null)
    val state: StateFlow<Result?> = _state


    fun dispose() {
        job?.cancel()
    }


    private suspend fun emitLocation(f: FakeLocation, isNewLocation: Boolean) {
        val currentTime = System.currentTimeMillis()
        val realtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()

        val l = Location("").apply {
            latitude = f.latitude
            longitude = f.longitude
            altitude = f.altitude
            accuracy = f.haccuracy.toFloat()
            speed = if (isNewLocation) f.velocity.toFloat() else 0f
            bearing = 0f
            time = currentTime
            elapsedRealtimeNanos = realtimeNanos
        }

        _state.emit(Result(l, isNewLocation))
    }


    fun launch(server: MockLocationWebServer) {
        job?.cancel()

        job = scope.launch {
            var timer: Job? = null

            server.state.collect { request ->
                if (request == null) {
                    return@collect
                }

                // stop timer
                timer?.cancel()
                timer = null

                // set
                emitLocation(request.location, true)

                if (request.location.repeatedlyUpdate) {
                    // start timer
                    timer = launch {
                        while (true) {
                            delay(1000)
                            emitLocation(request.location, false)
                        }
                    }
                }
            }
        }
    }


}




