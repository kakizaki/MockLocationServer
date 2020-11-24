package com.example.mocklocationserver.web.dto

import java.util.*

data class FakeLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val haccuracy: Double,
    // 繰り返し API を実行するかどうか
    val repeatedlyUpdate: Boolean,
    // 速度
    val velocity: Double
) {

    fun hasNaN(): Boolean {
        return latitude.isNaN()
            || longitude.isNaN()
            || altitude.isNaN()
            || haccuracy.isNaN()
    }
}
