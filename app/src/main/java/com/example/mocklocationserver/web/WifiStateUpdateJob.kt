package com.example.mocklocationserver.web

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.Exception





class WifiStateUpdateJob(val context: Context) {
    data class Result(val isEnabled: Boolean, val info: WifiInfo?) {
        fun infoIsNull(): Boolean {
            return info == null
        }
    }

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(Dispatchers.Default + supervisorJob)

    private var job: Job? = null

    private val _state = MutableStateFlow<WifiStateUpdateJob.Result?>(null)
    val state: StateFlow<WifiStateUpdateJob.Result?> = _state

    fun dispose() {
        job?.cancel()
    }


    private fun isChangeWifiState(previous: WifiStateUpdateJob.Result?, current: WifiStateUpdateJob.Result): Boolean {
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


    // HACK CoroutineScope を引数で渡して、withContext(Dispatchers.Defualt) の方がいい気がする。引数のスコープに合わせて終われるし。
    fun launch() {
        job?.cancel()

        job = scope.launch {

            var previous: WifiStateUpdateJob.Result? = null

            while (true) {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                    var info: WifiInfo? = null
                    try {
                        info = wifiManager.connectionInfo
                    } catch (e: Exception) {
                    }
                    val current = WifiStateUpdateJob.Result(wifiManager.isWifiEnabled, info)
                    if (isChangeWifiState(previous, current)) {
                        _state.emit(current);
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


