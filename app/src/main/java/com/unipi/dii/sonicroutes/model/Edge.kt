package com.unipi.dii.sonicroutes.model


// the  edge connects two crossings with noise as a weight and the number of measurements
class Edge(private val startCrossingId: Int, private val endCrossingId: Int, private val noise: Double, private val measurements: Int) {
    override fun toString(): String {
        return "Edge(start=$startCrossingId, end=$endCrossingId, noise=$noise, measurements=$measurements)"
    }
}