package com.unipi.dii.sonicroutes.model

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


class NavigationManager(private val map: GoogleMap) {

    private var currentRoute: Route? = null
    private var currentRouteIndex = 0

    public fun showRouteOnMap(route: Route) {
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

        // Start navigation through the route
        //navigateToNextLocation()

        // set the map camera to the first segment
        /*val firstSegment = segments[0]
        val start = firstSegment.getStart()
        val end = firstSegment.getEnd()
        val middle = LatLng(
            (start.latitude + end.latitude) / 2,
            (start.longitude + end.longitude) / 2
        )
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(middle, 15f))*/

        // get the map current location
        val currentLatLng = map.cameraPosition.target
        val destinationLatLng = segments[0].getEnd()
        //requestDirections(currentLatLng, destinationLatLng)

        // get the degrees between the two points
        val degrees = getDegrees(currentLatLng, destinationLatLng)
        // rotate the map camera to match the degrees

        Log.i("Bearings", "Current: ${map.cameraPosition.bearing}, Destination: $degrees")
        // update the camera bearing to match the device orientation
        val currentCameraPosition = map.cameraPosition
        val newCameraPosition = CameraPosition.Builder(currentCameraPosition)
            .bearing(-degrees) // Rotate map based on azimuth (negative to match device orientation)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))

        Log.i("Bearings Mod", "Current: ${map.cameraPosition.bearing}, Destination: $degrees")

    }

    private fun getDegrees(currentLatLng: LatLng, destinationLatLng: LatLng): Float {
        val lat1 = Math.toRadians(currentLatLng.latitude)
        val lon1 = Math.toRadians(currentLatLng.longitude)
        val lat2 = Math.toRadians(destinationLatLng.latitude)
        val lon2 = Math.toRadians(destinationLatLng.longitude)

        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        var bearing = atan2(y, x)
        bearing = Math.toDegrees(bearing)

        // Normalize the bearing to a compass bearing (between 0 and 360 degrees)
        val normalizedBearing = (bearing + 360) % 360

        // TODO try it

        return normalizedBearing.toFloat()
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