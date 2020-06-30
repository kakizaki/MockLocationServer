package com.example.mocklocationserver.web.dto

import java.util.*

data class RequestFakeLocation(
    val date: Date,
    val location: FakeLocation
)