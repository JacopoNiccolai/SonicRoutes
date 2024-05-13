package com.unipi.dii.sonicroutes.model

import android.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.PolylineOptions


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


        updateCameraBearing(map, map.cameraPosition.bearing)

    }

    private fun updateCameraBearing(googleMap: GoogleMap?, bearing: Float) {
        if (googleMap == null) return
        val camPos = CameraPosition
            .builder(
                googleMap.cameraPosition // current Camera
            )
            .bearing(bearing)
            .build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
    }

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