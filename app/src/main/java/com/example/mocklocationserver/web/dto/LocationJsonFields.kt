package com.example.mocklocationserver.web.dto

enum class LocationJsonFields(val prop: String) {
    Lat("latitude"),
    Lng("longitude"),
    Altitude("altitude"),
    HAccuracy("haccuracy"),
    RepeatedlyUpdate("repeatedlyUpdate"),
    Velocity("velocity")
}
