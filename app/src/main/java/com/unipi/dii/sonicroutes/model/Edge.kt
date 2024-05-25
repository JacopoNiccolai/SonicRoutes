package com.unipi.dii.sonicroutes.model

import com.google.android.gms.maps.model.LatLng


// the edge connects two crossings with noise as a weight and the number of measurements
class Edge
    (
    private val startCrossingId: Int,
    startCoordinate: LatLng?,
    private val endCrossingId: Int,
    endCoordinate: LatLng?,
    private val amplitude: Double,
    private val measurements: Int
) : Segment(
    startCoordinate ?: LatLng(0.0, 0.0), // Provide a default LatLng if null
    endCoordinate ?: LatLng(0.0, 0.0)
) {

    override fun toString(): String {
        return "Edge(start=$startCrossingId, startCoordinate=${getStartCoordinates().toFormattedString()}, end=$endCrossingId, endCoordinate=${getEndCoordinates().toFormattedString()}, noise=$amplitude, measurements=$measurements)"
    }

    fun toCsvEntry(): String {
        return "$startCrossingId, ${getStartCoordinates().toFormattedString()}, $endCrossingId, ${getEndCoordinates().toFormattedString()}, $amplitude, $measurements, 'Pisa'"    //we use "Pisa" by default because until now there're only data about this city
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

    // Extension function for LatLng? to format the string representation for a CSV
    private fun LatLng?.toFormattedString(): String {
        return if (this != null) {
            "(${latitude}; ${longitude})"
        } else {
            "null"
        }
    }

}