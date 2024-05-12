package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng

data class Crossing(val id: Int, val latitude: Double, val longitude: Double, val streetName: List<String>) {
    override fun toString(): String {
        return "Crossing(latitude=$latitude, longitude=$longitude, streetName='$streetName')"
    }

    fun getCoordinates(): LatLng {
        return LatLng(latitude, longitude)
    }

    fun getStreetName(): ArrayList<String> {
        return ArrayList(streetName)
    }

    fun getCrossingId(): Int {
        return id
    }
}
