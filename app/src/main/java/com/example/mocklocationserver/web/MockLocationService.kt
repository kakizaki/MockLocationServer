package com.example.mocklocationserver.web

import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*

class MockLocationService : LifecycleService() {

    companion object {
    }


    private val serviceNotification = ServiceNotification()

    private var webServer: MockLocationWebServer? = null

    private lateinit var locationClient: FusedLocationProviderClient
    private var isAvailableLocationClient = false
    private val locationCallback = object: LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            super.onLocationAvailability(p0)
        }
    }


    private var updateLocationJob = LocationUpdateJob()

    private var updateWifiStateJob = WifiStateUpdateJob(this)


    private var isReadyUpdateNotification = false


    private val mockLocation =
        MockLocationSetter(
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ),
            listOf("fused")
        )


    init {
        lifecycleScope.launchWhenStarted {
            launch {
                updateLocationJob.state.collect { result -> onUpdateLocation(result) }
            }
            launch {
                updateWifiStateJob.state.collect { result -> onUpdateWifiState(result) }
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

        isReadyUpdateNotification = false

        webServer?.stop()

        updateLocationJob.dispose()

        updateWifiStateJob.dispose()

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



    fun registerFusedLocationProviderClient() {
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
                        }
                        catch (e: SecurityException) {

                        }
                    }
                } else {
                    // not available
                }
                break
            }
        }
    }


    fun unregisterLocationRequest() {
        if (isAvailableLocationClient) {
            locationClient?.let {
                it.removeLocationUpdates(locationCallback)
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
            ServiceNotification.INTENT_ACTION_STOP -> {
                stopSelf()
            }
            else -> run {
                // check allow Mock Location Application
                if (MockLocationUtility.isEnabledMockLocationApp(this) == false) {
                    try {
                        MockLocationUtility.goApplicationDevelopmentSettings((this))
                        Toast.makeText(this, R.string.toast_need_set_mocklocation_app, Toast.LENGTH_LONG).show()
                    }
                    catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.toast_need_developer_mode, Toast.LENGTH_LONG).show()
                    }
                    stopSelf()
                    return@run
                }

                //
                try {
                    webServer?.stop()
                    webServer = MockLocationWebServer(this, 8080, lifecycleScope).apply {
                        start()
                    }
                }
                catch (e: Exception) {
                    Toast.makeText(this, R.string.toast_failed_start_webserver, Toast.LENGTH_LONG).show()
                    stopSelf()
                    return@run
                }

                isReadyUpdateNotification = true
                webServer?.let {
                    updateLocationJob.launch(it)
                    updateWifiStateJob.launch()
                }

                val text = getNotificationText()
                serviceNotification.updateNotification_StopService(this, text)
            }
        }
        return START_NOT_STICKY
    }


    fun onNotRegisteredMockLocationApp() {
        val s = getString(R.string.toast_need_set_mocklocation_app)
        serviceNotification.updateNotification_StopService(this, s)
    }

    fun onUpdateLocation(r: LocationUpdateJob.Result?) {
        if (isReadyUpdateNotification == false) {
            return
        }
        if (r == null) {
            return
        }

        println("update mock location: ${r.l}")
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            try {
                mockLocation.set(r.l, locationManager)
            }
            catch (e: SecurityException) {
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
                    if (isReadyUpdateNotification) {
                        val tasks = mockLocation.trySet(r.l, locationClient)
                        if (tasks == null) {
                            onNotRegisteredMockLocationApp()
                        }
                        // TODO error handling of each tasks
                    }
                }
            }
        }

        val text = getNotificationText()
        serviceNotification.updateNotification_StopService(this, text)
    }

    private fun onUpdateWifiState(r: WifiStateUpdateJob.Result?) {
        if (isReadyUpdateNotification == false) {
            return
        }
        if (r == null) {
            return
        }

        val text = getNotificationText()
        serviceNotification.updateNotification_StopService(this, text)
    }




    fun getNotificationText(): String {
        val location = updateLocationJob.state.value
        val wifi = updateWifiStateJob.state.value

        var locationText = ""
        location?.let {
            val date = Date(it.l.time)
            val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
            locationText = " (${tf.format(date)} lat:${it.l.latitude} lng:${it.l.longitude} alt:${it.l.altitude} acc:${it.l.accuracy})"
        }

        if (wifi == null) {
            return "serve to :8080. " + locationText
        }

        if (wifi.isEnabled == false) {
            return "wifi is Disabled. " + locationText
        }

        if (wifi.info == null) {
            return "wifi is Disconnected. " + locationText
        }

        val ip = wifi.info.ipAddress ?: 0
        if (ip == 0) {
            return "wifi is Disconnected. " + locationText
        }

        val i1 = ip and 0xff
        val i2 = (ip ushr 8) and 0xff
        val i3 = (ip ushr 16) and 0xff
        val i4 = (ip ushr 24) and 0xff
        return "serve to ${i1}.${i2}.${i3}.${i4}:8080. " + locationText
    }
}


/**
 * android への通知
 */
class ServiceNotification {
    companion object {
        const val CHANNEL_ID = "MOCK_LOCATION_SERVICE_CHANNEL"

        const val NOTIFICATION_ID = 1
        const val INTENT_ACTION_STOP = "INTENT_ACTION_STOP"
    }


    /**
     * NotificationChannelの作成
     * O以降は必須
     */
    private fun registerNotificaitonChannel(c: Context) {
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            val m = NotificationManagerCompat.from(c)
            val channel = NotificationChannel(
                CHANNEL_ID,
                c.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            m.createNotificationChannel(channel)
        }
    }

    /**
     * NotificationChannelの削除
     */
    private fun unregisterNotificationChannel(c: Context) {
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            val m = NotificationManagerCompat.from(c)
            m.deleteNotificationChannel(CHANNEL_ID)
        }
    }

    /**
     * Serviceをフォアグラウンドサービスにする
     */
    fun makeForegroundService(s: Service) {
        registerNotificaitonChannel(s)

        val c = s.applicationContext

        val contentIntent = Intent(c, MainActivity::class.java).let {
            PendingIntent.getActivity(c, 0, it, 0)
        }

        val notification = NotificationCompat.Builder(c, CHANNEL_ID)
            .setContentTitle(s.getString(R.string.notification_channel_name))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText("aaa")
            .setContentIntent(contentIntent)
            .build()

        s.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 通知の破棄
     */
    fun disposeNotification(c: Context) {
        val m = NotificationManagerCompat.from(c)
        m.cancel(NOTIFICATION_ID)

        unregisterNotificationChannel(c)
    }

    /**
     * 通知の更新
     */
    fun updateNotification_StopService(c: Context, text: String) {
        val contentIntent = Intent(c, MainActivity::class.java).let {
                PendingIntent.getActivity(c, 0, it, 0)
            }

        val actionStop = Intent(c, MockLocationService::class.java).apply {
                action = INTENT_ACTION_STOP
            }.let {
                PendingIntent.getService(c, 0, it, 0)
            }.let {
                NotificationCompat.Action(R.drawable.ic_launcher_background, c.getString(R.string.notification_stop_service), it)
            }

        val notification = NotificationCompat.Builder(c, CHANNEL_ID)
            .setContentTitle(c.getString(R.string.notification_channel_name))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(actionStop)
            .build()

        val m = NotificationManagerCompat.from(c)
        m.notify(NOTIFICATION_ID, notification)

    }


}



