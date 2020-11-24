package com.example.mocklocationserver.web.dto

enum class LocationJsonFields(val prop: String) {
    lat("latitude"),
    lng("longitude"),
    alt("altitude"),
    hacc("haccuracy"),
    repeatedlyUpdate("repeatedlyUpdate"),
    velocity("velocity")
}
