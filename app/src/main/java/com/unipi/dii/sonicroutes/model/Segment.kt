package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng

class Segment (private val start: LatLng, private val end: LatLng) {
    fun getStart(): LatLng = start
    fun getEnd(): LatLng = end

}