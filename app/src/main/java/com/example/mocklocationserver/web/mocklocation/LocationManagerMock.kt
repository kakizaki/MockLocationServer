package com.example.mocklocationserver.web.mocklocation

import android.annotation.SuppressLint
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource

class LocationManagerMock(
    val locationManager: LocationManager,
    val providers: List<String>
) {

   fun setLocation(l: Location): Task<Void?> {
        val tcs = TaskCompletionSource<Void>()

        for (p in providers) {
            val l2 = Location(l)
            l2.provider = p

            // HACK: android 10 の場合: addTestProvider は同じ名前の provider が既に追加されていた場合に例外を投げる
            //      ドキュメントには "同じ名前の provider は置き換える" とあるのでバグなのでは.
            try {
                locationManager.setTestProviderLocation(p, l2)
            } catch (e: IllegalArgumentException) {
                // provider が追加されていない
                tcs.setException(e)
                return tcs.task
            }
        }
        tcs.setResult(null)
        return tcs.task
    }


    @SuppressLint("WrongConstant")
    fun enterMockMode() {
        for (p in providers) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    p,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                locationManager.addTestProvider(
                    p,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
            }
            locationManager.setTestProviderEnabled(p, true)
        }
    }


    fun exitMockMode() {
        for (p in providers) {
            try {
                locationManager.removeTestProvider(p)
            } catch (e: IllegalArgumentException) {
                // もともと追加されていないので気にしない
            }
        }
    }
}