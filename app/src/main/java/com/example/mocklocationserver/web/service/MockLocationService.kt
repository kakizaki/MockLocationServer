package com.example.mocklocationserver.web.service

import android.app.PendingIntent
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mocklocationserver.web.R
import com.example.mocklocationserver.web.data.InMemoryLocationRequestRepository
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import com.example.mocklocationserver.web.mocklocation.awaitTask
import com.example.mocklocationserver.web.server.MockLocationWebServer
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MockLocationService : LifecycleService() {

    companion object {
        const val TAG = "MockLocationService"

        private val WEB_SERVER_PORT_NUMBER = 8080
    }

    private val serviceNotification = MockLocationServiceNotification()

    @Inject
    lateinit var locationRequestRepository: InMemoryLocationRequestRepository

    @Inject
    lateinit var locationProvider: MockLocationProvideService

    @Inject
    lateinit var wifiInfoCollector: WifiInfoCollectService


    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
            Log.i(TAG, "$p0")
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            super.onLocationAvailability(p0)
            Log.i(TAG, "$p0")
        }
    }

    private lateinit var mockLocationInjector: MockLocationInjector
    private lateinit var webServer: MockLocationWebServer
    private var wifiInfo: String = ""
    private var locationInfo: String = ""


    override fun onCreate() {
        super.onCreate()

        mockLocationInjector = MockLocationInjector(
            this,
            getSystemService(LOCATION_SERVICE) as LocationManager,
            LocationServices.getFusedLocationProviderClient(this),
            lifecycleScope + Dispatchers.Default
        )

        webServer = MockLocationWebServer(
            WEB_SERVER_PORT_NUMBER, this, locationRequestRepository
        )

        serviceNotification.makeForegroundService(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationProvider.locationFlow.collect {
                    updateFakeLocation(it)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                wifiInfoCollector.state.filterNotNull().collect {
                    updateWifiInfo(it)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mockLocationInjector.notifyState.collect {
                    updateNotification()
                }
            }
        }

        registerFusedLocationProviderClient()
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")

        webServer?.stop()

        serviceNotification.disposeNotification(this)

        mockLocationInjector.stop()

        mockLocationInjector.fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }


    private fun registerFusedLocationProviderClient() {
        val context = this
        val looper = Looper.getMainLooper()

        lifecycleScope.launch {
            while (isActive) {
                if (mockLocationInjector.checkAvailability()) {
                    try {
                        // 5 分程度 LocationClient の呼び出しをしない場合、接続が切れる
                        // 接続が切れることで setMockMode が解除される
                        // 接続を維持するため requestLocationUpdates を行う
                        val request = LocationRequest.create()
                        request.priority = LocationRequest.PRIORITY_NO_POWER

                        mockLocationInjector.fusedLocationProviderClient.requestLocationUpdates(
                            request, locationCallback, looper
                        ).awaitTask()
                        break
                    } catch (e: SecurityException) {
                    }
                }

                delay(10.seconds)
                continue
            }
        }
    }


//    override fun onBind(intent: Intent): IBinder {
//        TODO("Return the communication channel to the service.")
//    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        println("onStartCommand")

        when (intent?.action) {
            MockLocationServiceNotification.INTENT_ACTION_STOP -> {
                stopSelf()
            }

            else -> {
                onStartService()
            }
        }
        return START_NOT_STICKY
    }


    private fun onStartService() {
        mockLocationInjector.start()

        try {
            if (webServer.wasStarted() == false) {
                webServer.start()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this, R.string.toast_failed_start_webserver, Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }
    }


    private fun updateFakeLocation(location: Location) {
        println("update mock location: ${location}")

        if (mockLocationInjector.setLocation(location)) {
            locationInfo = toLocationInfoString(location)
            updateNotification()
        }
    }


    private fun updateWifiInfo(r: WifiInfoCollectService.Result) {
        wifiInfo = toWifiInfoString(r)
        updateNotification()
    }

    private fun updateNotification() {
        when (mockLocationInjector.notifyState.value) {
            MockLocationInjector.State.TRY_ENTER_MOCK_MODE -> {
                serviceNotification.updateNotification(
                    this, getString(R.string.notification_enter_mockmode_start)
                )
            }

            MockLocationInjector.State.RETRY_ENTER_MOCK_MODE -> {
                serviceNotification.updateNotification(
                    this, getString(R.string.notification_enter_mockmode_faulted)
                )
            }

            MockLocationInjector.State.NOT_SET_MOCK_LOCATION_APP -> {
                val intent = MockLocationUtility.createApplicationDevelopmentSettings().let {
                    PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
                }
                serviceNotification.updateNotification(
                    this, getString(R.string.notification_need_set_mocklocation_app), intent
                )
            }

            MockLocationInjector.State.NO_PERMISSION -> {
                serviceNotification.updateNotification(
                    this, getString(R.string.notification_need_location_permission)
                )
            }

            MockLocationInjector.State.SUCCESS_ENTER_MOCK_MODE, MockLocationInjector.State.SUCCESS_ENTER_MOCK_MODE_ONLY_LOCATION_MANAGER -> {
                serviceNotification.updateNotification(this, "$wifiInfo $locationInfo")
            }
        }
    }


    private fun toLocationInfoString(location: Location): String {
        val date = Date(location.time)
        val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        return " (${tf.format(date)} lat:${location.latitude} lng:${location.longitude} alt:${location.altitude} acc:${location.accuracy})"
    }

    private fun toWifiInfoString(info: WifiInfoCollectService.Result?): String {
        if (info == null) {
            return "serve to :${WEB_SERVER_PORT_NUMBER}."
        }

        if (info.isEnabled == false) {
            return "wifi is Disabled."
        }

        if (info.info == null) {
            return "wifi is Disconnected."
        }

        val ip = info.info.ipAddress ?: 0
        if (ip == 0) {
            return "wifi is Disconnected."
        }

        val i1 = ip and 0xff
        val i2 = (ip ushr 8) and 0xff
        val i3 = (ip ushr 16) and 0xff
        val i4 = (ip ushr 24) and 0xff
        return "serve to ${i1}.${i2}.${i3}.${i4}:${WEB_SERVER_PORT_NUMBER}."
    }

}







