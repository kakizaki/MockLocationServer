package com.example.mocklocationserver.web.data

import com.example.mocklocationserver.web.dto.LocationRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class InMemoryLocationRequestRepository {
    private val _location = MutableSharedFlow<LocationRequest>(0, 1, BufferOverflow.DROP_OLDEST)

    val location: Flow<LocationRequest>
    get() = _location

    suspend fun set(l: LocationRequest) {
        _location.emit(l)
    }

    fun update(l: LocationRequest) {
        _location.tryEmit(l)
    }
}