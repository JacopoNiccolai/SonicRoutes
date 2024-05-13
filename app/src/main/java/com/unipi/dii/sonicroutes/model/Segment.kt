package com.unipi.dii.sonicroutes.model

import android.util.Log
import com.google.android.gms.maps.model.LatLng

class Segment (private val start: LatLng, private val end: LatLng, private val amplitude: Double) {
    fun getStart(): LatLng = start
    fun getEnd(): LatLng = end
    fun getAmplitude(): Double = amplitude

    fun printSegment() {
        Log.d("Segment", "Segment from $start to $end with amplitude $amplitude")
    }

}