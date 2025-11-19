// FireReport.kt
package com.example.flare_capstone

data class FireReport(
    val name: String = "",
    val contact: String = "",
    val type: String = "",
    val date: String = "",
    val reportTime: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val exactLocation: String = "",
    val location: String = "",
    val photoBase64: String = "",
    val timeStamp: Long = 0L,
    val status: String = "Pending",
    var fireStationName: String = "Canocotan Fire Station",
    val adminNotif:Boolean = false,
    val read: Boolean = false
)
