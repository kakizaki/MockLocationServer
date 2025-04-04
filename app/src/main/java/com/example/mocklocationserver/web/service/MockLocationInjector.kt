package com.example.mocklocationserver.web.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import com.example.mocklocationserver.web.mocklocation.FusedLocationProviderClientMock
import com.example.mocklocationserver.web.mocklocation.LocationManagerMock
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import com.example.mocklocationserver.web.mocklocation.awaitTask
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MockLocationInjector(
    val context: Context,
    val locationManager: LocationManager,
    val fusedLocationProviderClient: FusedLocationProviderClient,
    val scope: CoroutineScope
) {

    enum class State {
        TRY_ENTER_MOCK_MODE,
        NOT_SET_MOCK_LOCATION_APP,
        NO_PERMISSION,
        RETRY_ENTER_MOCK_MODE,
        SUCCESS_ENTER_MOCK_MODE,
        SUCCESS_ENTER_MOCK_MODE_ONLY_LOCATION_MANAGER;

        fun hasEnterMockMode(): Boolean {
            return when (this) {
                SUCCESS_ENTER_MOCK_MODE,
                SUCCESS_ENTER_MOCK_MODE_ONLY_LOCATION_MANAGER -> true
                else -> false
            }
        }
    }

    companion object {
        private val enterMockModeRetrySpan = 5.seconds
    }


    private val _notifyState = MutableStateFlow<State>(State.TRY_ENTER_MOCK_MODE)
    val notifyState = _notifyState.asStateFlow()


    private val locationManagerMock = LocationManagerMock(
        locationManager,
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
    )

    private val fusedLocationProviderMock = FusedLocationProviderClientMock(
        fusedLocationProviderClient,
        listOf("fused")
    )

    private var enterMockModeJob: Job? = null

    private suspend fun waitNeedEnterMockMode() {
        notifyState.first { it.hasEnterMockMode() == false }
    }

    fun start() {
        if (enterMockModeJob?.isActive == true) {
            return
        }

        enterMockModeJob = scope.launch {
            while (isActive) {
                waitNeedEnterMockMode()

                if (MockLocationUtility.Companion.isEnabledMockLocationApp(context) == false) {
                    _notifyState.value = State.NOT_SET_MOCK_LOCATION_APP
                    delay(enterMockModeRetrySpan)
                    continue
                }
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    _notifyState.value = State.NO_PERMISSION
                    delay(enterMockModeRetrySpan)
                    continue
                }
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    _notifyState.value = State.NO_PERMISSION
                    delay(enterMockModeRetrySpan)
                    continue
                }

                //
                locationManagerMock.enterMockMode()

                //
                _notifyState.value = if (checkAvailability()) {
                    try {
                        fusedLocationProviderMock.enterMockMode().awaitTask()
                        State.SUCCESS_ENTER_MOCK_MODE
                    } catch (_: Exception) {
                        State.SUCCESS_ENTER_MOCK_MODE_ONLY_LOCATION_MANAGER
                    }
                } else {
                    State.SUCCESS_ENTER_MOCK_MODE_ONLY_LOCATION_MANAGER
                }
            }
        }
    }

    suspend fun checkAvailability(): Boolean {
        val gaa = GoogleApiAvailability.getInstance()
        while (true) {
            val result = gaa.isGooglePlayServicesAvailable(context)
            if (result == ConnectionResult.SERVICE_UPDATING) {
                // waiting for updating
                delay(10.seconds)
                continue
            }

            if (result == ConnectionResult.SUCCESS) {
                break
            } else {
                return false
            }
        }

        try {
            gaa.checkApiAvailability(fusedLocationProviderMock.client).awaitTask()
            return true
        }
        catch (_: Exception) {
            return false
        }
    }


    fun stop() {
        enterMockModeJob?.cancel()

        try {
            locationManagerMock.exitMockMode()
        } catch (_: SecurityException) {
        }
        try {
            fusedLocationProviderMock.exitMockMode()
        } catch (_: SecurityException) {
        }
    }


    fun setLocation(l: Location): Boolean {
        val state = notifyState.value
        if (state.hasEnterMockMode() == false) {
            return false
        }

        try {
            locationManagerMock.setLocation(l)
            if (state == State.SUCCESS_ENTER_MOCK_MODE) {
                fusedLocationProviderMock.setLocation(l).addOnFailureListener {
                    _notifyState.value = State.TRY_ENTER_MOCK_MODE
                }
            }
            return true
        } catch (_: SecurityException) {
            _notifyState.value = State.TRY_ENTER_MOCK_MODE
            return false
        }
    }

}