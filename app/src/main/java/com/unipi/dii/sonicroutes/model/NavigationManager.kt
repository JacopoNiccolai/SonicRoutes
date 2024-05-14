package com.unipi.dii.sonicroutes.model

import android.graphics.Color
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


class NavigationManager(private val map: GoogleMap) {

    private var currentRoute: Route? = null
    private var currentRouteIndex = 0

    fun showRouteOnMap(route: Route) {
        currentRoute = route
        currentRouteIndex = 0

        val segments = route.getSegments()
        for (segment in segments) {
            val start = segment.getStart()
            val end = segment.getEnd()
            map.addPolyline(
                PolylineOptions()
                    .add(start, end)
                    .width(5f)
                    .color(Color.BLUE)
            )
        }

        resetMapOrientation()


        //get the map current location
        val currentLatLng = map.cameraPosition.target
        Log.i("Coords", "Current LATLON: $currentLatLng")
        val destinationLatLng = segments[0].getEnd()
        Log.i("Coords", "Destination LATLON: $destinationLatLng")
        //requestDirections(currentLatLng, destinationLatLng)

        // get the degrees between the two points
        val degrees = getDegrees(currentLatLng, destinationLatLng)
        Log.i("Deggghhh", "Deg: $degrees")
        // rotate the map camera to match the degrees

        Log.i("Bearings", "Current: ${map.cameraPosition.bearing}, Destination: $degrees")
        // update the camera bearing to match the device orientation
        val currentCameraPosition = map.cameraPosition
        val newCameraPosition = CameraPosition.Builder(currentCameraPosition)
            .bearing(degrees) // Rotate (negative to match device orientation)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))

        Log.i("Bearings Mod", "Current: ${map.cameraPosition.bearing}, Destination: $degrees")

    }

    private fun getDegrees(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        return (Math.toDegrees(Math.atan2(y, x)).toFloat() + 360) % 360
    }


    private fun resetMapOrientation() {
        val currentCameraPosition = map.cameraPosition
        val newCameraPosition = CameraPosition.Builder(currentCameraPosition)
            .bearing(0f) // Set bearing to 0 degrees (north)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))

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