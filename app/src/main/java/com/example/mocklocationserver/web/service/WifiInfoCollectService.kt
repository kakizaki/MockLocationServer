package com.example.mocklocationserver.web.service

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.example.mocklocationserver.web.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


class WifiInfoCollectService @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) {
    data class Result(val isEnabled: Boolean, val info: WifiInfo?) {
        fun infoIsNull(): Boolean {
            return info == null
        }

        fun isUpdated(some: Result?): Boolean {
            if (some == null) {
                return true
            }

            if (some.isEnabled != isEnabled) {
                return true
            }

            if (some.infoIsNull() != infoIsNull()) {
                return true
            }

            if (infoIsNull()) {
                return false
            }

            if (some.info?.ssid != info?.ssid) {
                return true
            }

            if (some.info?.ipAddress != info?.ipAddress) {
                return true
            }

            return false
        }
    }


    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getWifiInfoFow() = flow {
        var previous: Result? = null
        while (true) {
            currentCoroutineContext().ensureActive()

            val info = try {
                wifiManager.connectionInfo
            } catch (e: Exception) {
                null
            }

            val current = Result(wifiManager.isWifiEnabled, info)
            if (current.isUpdated(previous)) {
                emit(current)
            }
            previous = current
            delay(1.seconds)
        }
    }

    val state = getWifiInfoFow().stateIn(scope, SharingStarted.Lazily, null)
}



