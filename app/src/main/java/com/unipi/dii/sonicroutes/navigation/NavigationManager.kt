package com.unipi.dii.sonicroutes.navigation

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.*
import com.google.android.gms.maps.model.MarkerOptions
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.model.Route


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

            val currentLatLng = segment.getStart()
            val destinationLatLng = segment.getEnd()
            val degrees = angleFromNorth(currentLatLng, destinationLatLng)

            if (segments.indexOf(segment) == 0) { // don't add an arrow for the first segment
                continue
            }else{ // if this is not the first
                map.addMarker(
                    MarkerOptions()
                        .position(start)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.arrow_icon))
                        .anchor(0.5f, 0.5f)
                        .rotation(degrees)
                        .flat(true)
                )
            }
            if(segments.indexOf(segment) == segments.size-1) { // if this is the last segment I add a green marker
                map.addMarker(
                    MarkerOptions()
                        .position(end)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

        }

        if (segments.size > 1) {
            //get the map current location
            val sourceLatLng = segments[0].getEnd()
            val destinationLatLng = segments[1].getEnd()

            // get the degrees between the two points
            val degrees = angleFromNorth(sourceLatLng, destinationLatLng)

            setMapRotation(degrees, sourceLatLng)

        }

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

    private fun setMapRotation(angle: Float, position: LatLng) {
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.builder()
                .target(position)
                .zoom(16.5f)
                .bearing(angle)
                .build()
        ))
    }

    fun updateAlignment(){

        if (currentRoute != null && currentRouteIndex < currentRoute!!.getSegments().size) {
            val segments = currentRoute!!.getSegments()
            val currentLatLng = segments[currentRouteIndex].getEnd()
            currentRouteIndex++
            val destinationLatLng = segments[currentRouteIndex].getEnd()

            val degrees = angleFromNorth(currentLatLng, destinationLatLng)

            setMapRotation(degrees, currentLatLng)
        }

    }

}