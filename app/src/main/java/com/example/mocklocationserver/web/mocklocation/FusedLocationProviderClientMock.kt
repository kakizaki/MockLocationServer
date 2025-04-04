package com.example.mocklocationserver.web.mocklocation

import android.Manifest
import android.location.Location
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks


class FusedLocationProviderClientMock(
    val client: FusedLocationProviderClient,
    val providers: List<String>
) {

    fun setLocation(l: Location): Task<Void?> {
        val tasks = mutableListOf<Task<Void>>()
        for (p in providers) {
            val l2 = Location(l)
            l2.provider = p

            try {
                val t = client.setMockLocation(l2)
                tasks.add(t)
            }
            catch (e: SecurityException) {
                val tcs = TaskCompletionSource<Void>()
                tcs.setException(e)
                return tcs.task
            }
        }
        return Tasks.whenAll(tasks)
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun enterMockMode(): Task<Void?> {
        return client.setMockMode(true)
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun exitMockMode(): Task<Void?> {
        return client.setMockMode(false)
    }
}


