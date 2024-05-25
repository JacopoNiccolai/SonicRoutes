package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng


// the  edge connects two crossings with noise as a weight and the number of measurements
class Edge(
    private val startCrossingId: Int,
    private val startCoordinate: LatLng?,
    private val endCrossingId: Int,
    private val endCoordinate: LatLng?,
    private val amplitude: Double,
    private val measurements: Int
) {

    // Secondary constructor omitting startCoordinate and endCoordinate
    constructor(
        startCrossingId: Int,
        endCrossingId: Int,
        amplitude: Double,
        measurements: Int
    ) : this(startCrossingId, null, endCrossingId, null, amplitude, measurements)

    override fun toString(): String {
        return "Edge(start=$startCrossingId, end=$endCrossingId, noise=$amplitude, measurements=$measurements)"
    }

    fun toCsvEntry(): String {
        return "$startCrossingId, ${startCoordinate.toString()}, $endCrossingId, ${endCoordinate.toString()}, $amplitude, $measurements, 'Pisa'"    //we use "Pisa" by default because until now there're only data about this city
    }

    fun getStartCrossingId(): Int {
        return startCrossingId
    }

    fun getEndCrossingId(): Int {
        return endCrossingId
    }

    fun getAmplitude(): Double {
        return amplitude
    }

    fun getMeasurements(): Int {
        return measurements
    }

    fun getStartCoordinate(): LatLng? {
        return startCoordinate
    }

    fun getEndCoordinate(): LatLng? {
        return endCoordinate
    }

}

// ovverride LatLng to string
fun LatLng?.toString(): String {
    return if (this != null) {
        "(${latitude}; ${longitude})"
    } else {
        "null"
    }
}