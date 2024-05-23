package com.unipi.dii.sonicroutes.model

import android.util.Log

class Route(private val segments: List<Segment>) {

    fun getSegments(): List<Segment> = segments

}
