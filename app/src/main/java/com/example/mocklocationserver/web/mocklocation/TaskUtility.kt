package com.example.mocklocationserver.web.mocklocation

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> Task<T>.awaitTask(): T {
    return suspendCoroutine<T> { continuation ->
        this.addOnSuccessListener {
            continuation.resume(it)
        }.addOnFailureListener {
            continuation.resumeWithException(it)
        }
    }
}