package com.unipi.dii.sonicroutes.ui.routes

import android.content.ContentValues.TAG
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.PolylineOptions
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.databinding.FragmentRouteBinding
import com.unipi.dii.sonicroutes.model.Edge
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class RouteFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentRouteBinding
    private var map: GoogleMap? = null
    private var fileName: String? = null // received as argument
    private val minAmplitude = 0.0 // this and the following are used to map the amplitude to a color
    private val maxAmplitude = 5000.0
    private lateinit var loadingLayout: FrameLayout


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_route, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get filename from arguments
        fileName = arguments?.getString("fileName")?.trim()

        val mapFragment = childFragmentManager.findFragmentById(R.id.route_fragment_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.buttonBackToDashboard.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadingLayout = binding.loadingLayout

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        loadRouteFromFile(fileName)
    }

    private fun loadRouteFromFile(fileName: String?) {
        fileName?.let { file ->
            try {
                val filesDir = requireContext().filesDir
                val routeFile = File(filesDir, file)

                if (!routeFile.exists()) {
                    Log.e(TAG, "File not found: $file")
                    return
                }

                val reader = BufferedReader(FileReader(routeFile))
                // Skip the first line (it's the header)
                reader.readLine()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // Process the file content line by line
                    val data = line!!.split(",")
                    if (data.size == 7) {
                        try {
                            val edge = parseEdge(line!!)
                            addPointToPolyline(edge)
                        } catch (e: NumberFormatException) {
                            Log.e(TAG, "Error parsing data: ${e.message}")
                            e.printStackTrace()
                        }
                    } else {
                        Log.e(TAG, "Invalid line format: $line")
                    }
                }
                reader.close()

                loadingLayout.visibility = View.GONE

                binding.buttonBackToDashboard.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e(TAG, "Error reading file: ${e.message}")
                // Print a more detailed log
                e.printStackTrace()
            }
        }
    }


    private fun addPointToPolyline(edge: Edge) {

        val startingCrossing = edge.getStartCoordinates()
        val endingCrossing = edge.getEndCoordinates()
        val pattern: List<PatternItem> = listOf(Dash(30f), Gap(5f))

        // Map the amplitude to a color
        val amplitude = edge.getAmplitude() / edge.getMeasurements()
        val color = mapAmplitudeToColor(amplitude)

        // Add a line segment to the map
        val polylineOptions = PolylineOptions()
            .add(startingCrossing)
            .add(endingCrossing)
            .pattern(pattern) // Apply the pattern
            .color(color) // Set the color
            .width(20f)
        map?.addPolyline(polylineOptions)

        // Center the map on the last point with animation
        endingCrossing.let { CameraUpdateFactory.newLatLngZoom(it, 16f) }
            .let { map?.moveCamera(it) }
    }

    private fun parseEdge(data: String): Edge {
        // Split the data by commas and trim spaces
        val parts = data.split(",").map { it.trim() }

        val startCrossingId = parts[0].toInt()

        // Extract and parse the first lat/lng pair
        val startLatLngString = parts[1].substringAfter("(").substringBefore(")")
        val startLatLngParts = startLatLngString.split(";").map { it.trim().toDouble() }
        val startLatLng = LatLng(startLatLngParts[0], startLatLngParts[1])

        val endCrossingId = parts[2].toInt()

        // Extract and parse the second lat/lng pair
        val endLatLngString = parts[3].substringAfter("(").substringBefore(")")
        val endLatLngParts = endLatLngString.split(";").map { it.trim().toDouble() }
        val endLatLng = LatLng(endLatLngParts[0], endLatLngParts[1])

        val amplitude = parts[4].toDouble()
        val measurements = parts[5].toInt()

        // Remove single quotes from the city name
        val city = parts[6].trim('\'')

        return Edge(startCrossingId, startLatLng, endCrossingId, endLatLng, amplitude, measurements)
    }

    private fun mapAmplitudeToColor(amplitude: Double): Int {
        if (amplitude > maxAmplitude)
            return Color.RED
        // Simple linear mapping
        val normalizedAmplitude = (amplitude - minAmplitude) / (maxAmplitude - minAmplitude)
        return Color.rgb(
            (normalizedAmplitude * 255).toInt(),
            ((1 - normalizedAmplitude) * 255).toInt(),
            0
        )
    }

}

