package com.unipi.dii.sonicroutes.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.unipi.dii.sonicroutes.R

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
    }

    private fun setupMap() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(10000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    val location = locationResult.locations.first()
                    updateMap(location)
                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateMap(location: Location) {
        val userLocation = LatLng(location.latitude, location.longitude)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onPause() {
        super.onPause()
        if (::fusedLocationProviderClient.isInitialized) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
