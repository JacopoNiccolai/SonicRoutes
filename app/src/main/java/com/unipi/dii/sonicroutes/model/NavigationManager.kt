package com.unipi.dii.sonicroutes.model

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.*

class NavigationManager(private val map: GoogleMap) {

    private var currentRoute: Route? = null
    private var currentRouteIndex = 0

    fun showRouteOnMap(route: Route, i: Int) {
        currentRoute = route
        currentRouteIndex = 0

        val segments = route.getSegments()
        for (segment in segments) {
            val start = segment.getStart()
            val end = segment.getEnd()
            val color = if (i == 1) Color.RED else Color.BLUE
            map.addPolyline(
                PolylineOptions()
                    .add(start, end)
                    .width(5f)
                    .color(color)
            )
        }

        //get the map current location
        val currentLatLng = map.cameraPosition.target
        val destinationLatLng = segments[0].getEnd()
        //requestDirections(currentLatLng, destinationLatLng)

        val bearing = map.cameraPosition.bearing

        // get the degrees between the two points
        val degrees = angleFromNorth(currentLatLng, destinationLatLng)
        Log.d("NavigationManager", "currentLatLng: $currentLatLng")
        Log.d("NavigationManager", "degrees: $degrees")
        Log.d("NavigationManager", "bearing: $bearing")

        val angle = bearing - degrees
        Log.d("NavigationManager", "angle: $angle")

        setMapRotation(degrees)

    }

    private fun angleFromNorth(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        var angle = atan2(y, x)
        angle = Math.toDegrees(angle)
        angle = (angle + 360) % 360

        return angle.toFloat()
    }

    private fun setMapRotation(angle: Float) {
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.builder()
                .target(map.cameraPosition.target)
                .zoom(18f)
                .bearing(angle)
                .build()
        ))
    }

    fun updateAlignment(){

        setMapRotation(0f)

        if (currentRoute != null) {
            val segments = currentRoute!!.getSegments()
            val currentLatLng = segments[currentRouteIndex].getEnd()
            currentRouteIndex++
            val destinationLatLng = segments[currentRouteIndex].getEnd()

            val degrees = angleFromNorth(currentLatLng, destinationLatLng)
            setMapRotation(degrees)
        }

        
    }



    /*private fun updateCameraBearing(map: GoogleMap) {
        // Rotate the map
        val currentCameraPosition = map.cameraPosition
        val newCameraPosition = CameraPosition.Builder(currentCameraPosition)
            .bearing() // Rotate map based on azimuth (negative to match device orientation)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
    }*/

    /*private fun requestDirections(origin: LatLng, destination: LatLng) {
        val context = GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key)) // Replace with your Google Maps API key
            .build()

        DirectionsApi.newRequest(context)
            .mode(TravelMode.WALKING)
            .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
            .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
            .setCallback(object : com.google.maps.PendingResult.Callback<DirectionsResult> {
                override fun onResult(result: DirectionsResult?) {
                    result?.routes?.let { routes ->
                        if (routes.isNotEmpty()) {
                            // Display the first route on the map
                            displayRoute(routes[0])
                        }
                    }
                }

                override fun onFailure(e: Throwable?) {
                    // Handle API request failure
                }
            })
    }

    private fun displayRoute(route: com.google.maps.model.DirectionsRoute) {
        val decodedPath = route.overviewPolyline.decodePath()

        val polylineOptions = PolylineOptions().apply {
            width(8f)
            color(ContextCompat.getColor(this@MapsActivity, R.color.colorPrimary))
            addAll(decodedPath)
        }

        mMap.addPolyline(polylineOptions)
    }*/


}