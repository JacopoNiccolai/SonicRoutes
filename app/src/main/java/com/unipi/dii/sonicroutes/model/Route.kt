package com.unipi.dii.sonicroutes.model

import android.util.Log

class Route(private val segments: List<Segment>) {
    fun getSegments(): List<Segment> = segments

    fun printRoute() {
        Log.d("Route", "Route with ${segments.size} segments")
        for (segment in segments) {
            segment.printSegment()
        }
    }
}
