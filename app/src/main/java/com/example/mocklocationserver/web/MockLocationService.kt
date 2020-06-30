package com.example.mocklocationserver.web

import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mocklocationserver.web.mocklocation.MockLocationSetter
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import java.sql.Date
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*

class MockLocationService : Service(), LocationUpdateJobCallback {

    companion object {
    }

    private val serviceNotification = ServiceNotification()

    private var webServer: MockLocationWebServer? = null

    private var updateJob = LocationUpdateJob(this)

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
                }

                serviceNotification.updateNotification_StopService(this, "serve to :8080")
            }
        }
        return START_NOT_STICKY
    }


    override fun setLocation(l: Location, isNewLocation: Boolean) {
        println("update mock location: ${l}")

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

            val date = Date(l.time)
            val tf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss Z")
            serviceNotification.updateNotification_StopService(
                this,
                "serve to :8080 (${tf.format(date)} lat:${l.latitude} lng:${l.longitude} alt:${l.altitude} acc:${l.accuracy})"
            )
        }
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



