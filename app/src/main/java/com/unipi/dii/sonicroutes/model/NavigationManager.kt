package com.unipi.dii.sonicroutes.model

import android.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
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
        val firstSegment = segments[0]
        val start = firstSegment.getStart()
        val end = firstSegment.getEnd()
        val middle = LatLng(
            (start.latitude + end.latitude) / 2,
            (start.longitude + end.longitude) / 2
        )
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(middle, 15f))

    }



}