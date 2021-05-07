package com.example.mocklocationserver.web.mocklocation

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.lang.IllegalArgumentException

class MockLocationSetter(
    val providersForLocationManager: List<String>,
    val providersForFusedLocationProviderClient: List<String>
) {

    fun set(l: Location, locationManager: LocationManager) {
        for (p in providersForLocationManager) {
            val l2 = Location(l)
            l2.provider = p

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
            locationManager.setTestProviderEnabled(p, true)
            locationManager.setTestProviderLocation(p, l2)
        }
    }

    fun exitMockMode(locationManager: LocationManager) {
        for (p in providersForLocationManager) {
            try {
                locationManager.removeTestProvider(p)
            }
            catch (e: IllegalArgumentException) {
            }
            catch (e: SecurityException) {
            }
        }
    }

    fun exitMockMode(locationClient: FusedLocationProviderClient) {
        try {
            locationClient.setMockMode(false)
        }
        catch (e: SecurityException) {
            // not care
        }
        _hasEnterMockMode = false
    }

    private var _hasEnterMockMode = false
    val hasEnterMockMode
        get() = _hasEnterMockMode

    fun tryEnterMockMode(client: FusedLocationProviderClient): Task<Void?>? {
        if (hasEnterMockMode) {
            val tcs = TaskCompletionSource<Void>()
            tcs.setResult(null)
            return tcs.task
        }

        try {
            val t = client.setMockMode(true)
            t.addOnSuccessListener { _ -> _hasEnterMockMode = true }
            return t
        } catch (e: SecurityException) {
            return null
        }
    }

    fun trySet(l: Location, client: FusedLocationProviderClient): Task<Void>? {
        val tasks = mutableListOf<Task<Void>>()
        for (p in providersForFusedLocationProviderClient) {
            val l2 = Location(l)
            l2.provider = p
            try {
                val t = client.setMockLocation(l2)
                tasks.add(t)
            } catch (e: SecurityException) {
                return null
            }
        }
        return Tasks.whenAll(tasks)
    }

}