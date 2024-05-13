package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng

data class Crossing(val id: Int, val coordinates: LatLng, val streetName: List<String>) {
    override fun toString(): String {
        return "Crossing(coordinates=($coordinates), streetName='$streetName')"
    }

    fun getCoordinates(): LatLng {
        return coordinates
    }

    fun getStreetName(): ArrayList<String> {
        return ArrayList(streetName)
    }

    fun getCrossingId(): Int {
        return id
    }
}
