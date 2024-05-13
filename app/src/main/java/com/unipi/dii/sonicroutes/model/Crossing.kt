package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng

data class Crossing(private val id: Int,private val coordinates: LatLng,private val streetName: List<String>) {
    override fun toString(): String {
        return "Crossing(coordinates=($coordinates), streetName='$streetName')"
    }

    fun getCoordinates(): LatLng {
        return coordinates
    }

    fun getStreetName(): ArrayList<String> {
        return ArrayList(streetName)
    }

    fun getId(): Int {
        return id
    }
}
