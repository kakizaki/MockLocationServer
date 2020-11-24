package com.example.mocklocationserver.web

import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import kotlinx.coroutines.Dispatchers
import java.sql.Date
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*

class MockLocationService : Service(), LocationUpdateJobCallback, WifiStateUpdateJobCallback {

    companion object {
    }

    private val serviceNotification = ServiceNotification()

    private var webServer: MockLocationWebServer? = null

    private var updateJob = LocationUpdateJob(this)

    private var updateWifiStateJob = WifiStateUpdateJob(this, this)


    private val lockInstance = Object()

    private var currentLocation: Location? = null

    private var currentWifiInfoResult: WifiInfoResult? = null



    private val mockLocation =
        MockLocationSetter(
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )
        )


    override fun onCreate() {
        super.onCreate()

        serviceNotification.makeForegroundService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")

        webServer?.stop()

        updateJob.dispose()

        updateWifiStateJob.dispose()

        serviceNotification.disposeNotification(this)

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager != null) {
            mockLocation.clear(locationManager)
        }
    }


    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("onStartCommand")

        action@ when (intent?.action) {
            ServiceNotification.INTENT_ACTION_STOP -> {
                stopSelf()
            }
            else -> {
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
                    return@action
                }

                // TODO check Wifi has connected

                //
                try {
                    webServer?.stop()
                    webServer = MockLocationWebServer(this, 8080).apply {
                        start()
                    }
                }
                catch (e: Exception) {
                    Toast.makeText(this, R.string.toast_failed_start_webserver, Toast.LENGTH_LONG).show()
                    stopSelf()
                    return@action
                }

                webServer?.let {
                    updateJob.launch(it)
                    updateWifiStateJob.launch()
                }

                val text = getNotificationText()
                serviceNotification.updateNotification_StopService(this, text)
            }
        }
        return START_NOT_STICKY
    }


    override fun setLocation(l: Location, isNewLocation: Boolean) {
        println("update mock location: ${l}")

        synchronized(lockInstance) {
            currentLocation = l
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager != null) {
            try {
                mockLocation.set(l, locationManager)
            }
            catch (e: SecurityException) {
                val s = getString(R.string.toast_need_set_mocklocation_app)
                serviceNotification.updateNotification_StopService(this, s)
                return
            }

            val text = getNotificationText()
            serviceNotification.updateNotification_StopService(this, text)
        }
    }


    override fun setWifiState(w: WifiInfoResult) {
        synchronized(lockInstance) {
            currentWifiInfoResult = w
        }

        val text = getNotificationText()
        serviceNotification.updateNotification_StopService(this, text)
    }




    fun getNotificationText(): String {
        var _currentLocation: Location? = null
        var _currentWifiInfoResult: WifiInfoResult? = null

        synchronized(lockInstance) {
            _currentLocation = currentLocation
            _currentWifiInfoResult = currentWifiInfoResult
        }

        var locationText = ""
        _currentLocation?.let {
            val date = Date(it.time)
            val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")
            locationText = " (${tf.format(date)} lat:${it.latitude} lng:${it.longitude} alt:${it.altitude} acc:${it.accuracy})"
        }

        if (_currentWifiInfoResult == null) {
            return "serve to :8080. " + locationText
        }

        if (_currentWifiInfoResult?.isEnabled == false) {
            return "wifi is Disabled. " + locationText
        }

        if (_currentWifiInfoResult?.info == null) {
            return "wifi is Disconnected. " + locationText
        }

        val ip = _currentWifiInfoResult?.info?.ipAddress ?: 0
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



