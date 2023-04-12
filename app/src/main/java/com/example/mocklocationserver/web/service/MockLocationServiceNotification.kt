package com.example.mocklocationserver.web.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mocklocationserver.web.MainActivity
import com.example.mocklocationserver.web.R

/**
 * android への通知
 */
class MockLocationServiceNotification {
    companion object {
        const val CHANNEL_ID = "MOCK_LOCATION_SERVICE_CHANNEL"

        const val NOTIFICATION_ID = 1
        const val INTENT_ACTION_STOP = "INTENT_ACTION_STOP"
    }


    /**
     * NotificationChannelの作成
     * O以降は必須
     */
    private fun registerNotificationChannel(c: Context) {
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
        registerNotificationChannel(s)

        val c = s.applicationContext

        val contentIntent = Intent(c, MainActivity::class.java).let {
            PendingIntent.getActivity(c, 0, it, PendingIntent.FLAG_IMMUTABLE)
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
    @SuppressLint("MissingPermission")
    fun updateNotification(c: Context, text: String) {
        val contentIntent = Intent(c, MainActivity::class.java).let {
            PendingIntent.getActivity(c, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val actionStop = Intent(c, MockLocationService::class.java).apply {
            action = INTENT_ACTION_STOP
        }.let {
            PendingIntent.getService(c, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }.let {
            NotificationCompat.Action(
                R.drawable.ic_launcher_background,
                c.getString(R.string.notification_stop_service),
                it
            )
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