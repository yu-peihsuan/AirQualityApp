package com.example.airquality.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 資料層共用的座標型別。刻意不用 Google Maps 的 LatLng，讓 Repository 與地圖 SDK 解耦。 */
data class Coordinates(val lat: Double, val lng: Double)

/** 兩點球面距離（公里）。 */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2.0)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
