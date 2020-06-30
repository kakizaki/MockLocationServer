package com.example.mocklocationserver.web.mocklocation

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import java.lang.IllegalArgumentException

class MockLocationSetter(val providers: List<String>) {

    fun set(l: Location, locationManager: LocationManager) {
        for (p in providers) {
            val l2 = Location(l)
            l2.provider = p

            locationManager.addTestProvider(
                p,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(p, true)
            locationManager.setTestProviderLocation(p, l2)
        }
    }

    fun clear(locationManager: LocationManager) {
        for (p in providers) {
            try {
                locationManager.removeTestProvider(p)
            }
            catch (e: IllegalArgumentException) {
            }
            catch (e: SecurityException) {
            }
        }
    }
}