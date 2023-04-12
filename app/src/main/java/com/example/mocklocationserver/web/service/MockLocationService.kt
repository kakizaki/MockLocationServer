package com.example.mocklocationserver.web.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mocklocationserver.web.server.MockLocationWebServer
import com.example.mocklocationserver.web.R
import com.example.mocklocationserver.web.data.InMemoryLocationRequestRepository
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MockLocationService : LifecycleService() {

    private val serviceNotification = MockLocationServiceNotification()

    @Inject
    lateinit var locationRequestRepository: InMemoryLocationRequestRepository

    @Inject
    lateinit var locationProvider: MockLocationProvideService

    @Inject
    lateinit var wifiInfoCollector: WifiInfoCollectService


    private lateinit var locationClient: FusedLocationProviderClient
    private var isAvailableLocationClient = false
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            super.onLocationAvailability(p0)
        }
    }

    private var webServer: MockLocationWebServer? = null
    private var wifiInfo: String = ""
    private var locationInfo: String = ""


    private val mockLocation =
        MockLocationSetter(
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ),
            listOf("fused")
        )


    init {
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
    }

    override fun onCreate() {
        super.onCreate()

        serviceNotification.makeForegroundService(this)

        registerFusedLocationProviderClient()
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")

        webServer?.stop()

        serviceNotification.disposeNotification(this)

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            mockLocation.exitMockMode(locationManager)
        }

        if (isAvailableLocationClient) {
            isAvailableLocationClient = false
            mockLocation.exitMockMode(locationClient)
            unregisterLocationRequest()
        }
    }


    private fun registerFusedLocationProviderClient() {
        val context = this
        val looper = Looper.getMainLooper()

        lifecycleScope.launchWhenCreated {
            val gaa = GoogleApiAvailability.getInstance()
            while (isActive) {
                val r = gaa.isGooglePlayServicesAvailable(context)
                if (r == ConnectionResult.SERVICE_UPDATING) {
                    // waiting for updating
                    delay(10 * 1000)
                    continue
                }

                if (r == ConnectionResult.SUCCESS) {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    gaa.checkApiAvailability(client).addOnSuccessListener {
                        locationClient = client
                        isAvailableLocationClient = true

                        // 5 分程度 LocationClient の呼び出しをしない場合、接続が切れる
                        // 接続が切れることで setMockMode が解除される
                        // 接続を維持するため requestLocationUpdates を行う
                        val request = LocationRequest.create()
                        request.priority = LocationRequest.PRIORITY_NO_POWER

                        // パーミッションはアクティビティで確認されている
                        try {
                            locationClient.requestLocationUpdates(request, locationCallback, looper)
                        } catch (e: SecurityException) {

                        }
                    }
                } else {
                    // not available
                }
                break
            }
        }
    }


    private fun unregisterLocationRequest() {
        if (isAvailableLocationClient) {
            locationClient.removeLocationUpdates(locationCallback)
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
        // check allow Mock Location Application
        if (MockLocationUtility.isEnabledMockLocationApp(this) == false) {
            try {
                MockLocationUtility.goApplicationDevelopmentSettings((this))
                Toast.makeText(
                    this,
                    R.string.toast_need_set_mocklocation_app,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.toast_need_developer_mode,
                    Toast.LENGTH_LONG
                ).show()
            }
            stopSelf()
            return
        }

        //
        try {
            webServer?.stop()
            webServer = MockLocationWebServer(8080, this, locationRequestRepository).apply {
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                R.string.toast_failed_start_webserver,
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }
//        updateNotification()
    }


    private fun onNotRegisteredMockLocationApp() {
        val s = getString(R.string.toast_need_set_mocklocation_app)
        serviceNotification.updateNotification(this, s)
    }

    private fun updateFakeLocation(location: Location) {
        println("update mock location: ${location}")
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            try {
                mockLocation.set(location, locationManager)
            } catch (e: SecurityException) {
                onNotRegisteredMockLocationApp()
                return
            }
        }

        if (isAvailableLocationClient) {
            val t = mockLocation.tryEnterMockMode(locationClient)
            if (t == null) {
                onNotRegisteredMockLocationApp()
                return
            }
            t.continueWith {
                if (it.isSuccessful) {
                    val tasks = mockLocation.trySet(location, locationClient)
                    if (tasks == null) {
                        onNotRegisteredMockLocationApp()
                    }
                    // TODO error handling of each tasks
                }
            }
        }

        locationInfo = toLocationInfoString(location)
        updateNotification()
    }



    private fun updateWifiInfo(r: WifiInfoCollectService.Result) {
        wifiInfo = toWifiInfoString(r)
        updateNotification()
    }

    private fun updateNotification() {
        serviceNotification.updateNotification(this, "$wifiInfo $locationInfo")
    }


    private fun toLocationInfoString(location: Location): String {
        val date = Date(location.time)
        val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        return " (${tf.format(date)} lat:${location.latitude} lng:${location.longitude} alt:${location.altitude} acc:${location.accuracy})"
    }

    private fun toWifiInfoString(info: WifiInfoCollectService.Result?): String {
        if (info == null) {
            return "serve to :8080."
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
        return "serve to ${i1}.${i2}.${i3}.${i4}:8080."
    }

}



