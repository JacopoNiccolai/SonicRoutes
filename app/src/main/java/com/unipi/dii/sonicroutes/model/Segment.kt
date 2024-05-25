package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng

open class Segment(private val startCoordinate: LatLng, private val endCoordinate: LatLng) {
    fun getStartCoordinates(): LatLng {
        return startCoordinate
    }

    fun getEndCoordinates(): LatLng {
        return endCoordinate
    }

}



