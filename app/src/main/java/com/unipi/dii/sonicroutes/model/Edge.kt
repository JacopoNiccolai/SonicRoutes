package com.unipi.dii.sonicroutes.model


// the  edge connects two crossings with noise as a weight and the number of measurements
class Edge(private val startCrossingId: Int, private val endCrossingId: Int, private val amplitude: Double, private val measurements: Int) {
    override fun toString(): String {
        return "Edge(start=$startCrossingId, end=$endCrossingId, noise=$amplitude, measurements=$measurements)"
    }

    fun toCsvEntry(): String {
        return "$startCrossingId, $endCrossingId, $amplitude, $measurements"
    }
}