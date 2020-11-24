package com.example.mocklocationserver.web

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.lang.Exception



interface WifiStateUpdateJobCallback {
    fun setWifiState(i: WifiInfoResult)
}

data class WifiInfoResult(val isEnabled: Boolean, val info: WifiInfo?) {
    fun infoIsNull(): Boolean {
        return info == null
    }
}


class WifiStateUpdateJob(val context: Context, val callback: WifiStateUpdateJobCallback) {
    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(Dispatchers.Default + supervisorJob)

    private var job: Job? = null


    fun dispose() {
        job?.cancel()

    }


    private fun isChangeWifiState(previous: WifiInfoResult?, current: WifiInfoResult): Boolean {
        if (previous == null) {
            return true
        }

        if (previous.isEnabled != current.isEnabled) {
            return true
        }

        if (previous.infoIsNull() != current.infoIsNull()) {
            return true
        }

        if (current.infoIsNull()) {
            return false
        }

        if (previous.info?.ssid != current.info?.ssid) {
            return true
        }

        if (previous.info?.ipAddress != current.info?.ipAddress) {
            return true
        }

        return false
    }


    // HACK Flow でいい気がするが
    fun launch() {
        job?.cancel()

        job = scope.launch {

            var previous: WifiInfoResult? = null

            while (true) {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                    var info: WifiInfo? = null
                    try {
                        info = wifiManager.connectionInfo
                    } catch (e: Exception) {
                    }
                    val current = WifiInfoResult(wifiManager.isWifiEnabled, info)
                    if (isChangeWifiState(previous, current)) {
                        callback.setWifiState(current)
                    }
                    previous = current
                }

                delay(1000)

                if (this.isActive == false) {
                    break
                }
            }
        }
    }

}


