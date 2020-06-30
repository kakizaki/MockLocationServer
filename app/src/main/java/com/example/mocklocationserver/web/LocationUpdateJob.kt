package com.example.mocklocationserver.web

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import kotlinx.coroutines.*
import java.util.*


interface LocationUpdateJobCallback {
    fun setLocation(l: Location, isNewLocation: Boolean)
}



class LocationUpdateJob(val callback: LocationUpdateJobCallback) {
    private val supervisorJob = SupervisorJob()

    private val scope =
        CoroutineScope(Dispatchers.Default + supervisorJob)

    private var job: Job? = null


    fun dispose() {
        job?.cancel()

    }


    fun launch(server: MockLocationWebServer) {
        job?.cancel()

        job = scope.launch {
            var latestRequestDate: Date? = null
            while (true) {
                // いずれかの場合まで、待機
                // * 新しいリクエストを受信した
                // * 1秒待機した
                // * webServer が停止した
                server.waitNewLocation(latestRequestDate, 1000)
                if (this.isActive == false) {
                    break
                }

                // alternate
                //withTimeoutOrNull(1000) {
                //    server?.waitChannel(latestRequestDate)
                //}

                //
                if (server.isAlive() == false) {
                    break
                }

                // 位置があれば、セットする
                val request = server.getLocation()
                if (request == null) {
                    continue
                }

                val isNewLocation = latestRequestDate != request.date
                latestRequestDate = request.date

                // 更新
                val currentTime = System.currentTimeMillis()
                val realtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()

                val l = Location("").apply {
                    latitude = request.location.latitude
                    longitude = request.location.longitude
                    altitude = request.location.altitude
                    accuracy = request.location.haccuracy.toFloat()
                    speed = 0f
                    bearing = 0f
                    time = currentTime
                    elapsedRealtimeNanos = realtimeNanos
                }
                callback.setLocation(l, isNewLocation)
            }
        }
    }
}




