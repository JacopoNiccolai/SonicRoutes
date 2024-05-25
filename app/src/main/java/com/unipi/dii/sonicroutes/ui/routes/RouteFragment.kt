package com.unipi.dii.sonicroutes.ui.routes

import android.content.ContentValues.TAG
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.PolylineOptions
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.databinding.FragmentHomeBinding
import com.unipi.dii.sonicroutes.databinding.FragmentRouteBinding
import com.unipi.dii.sonicroutes.network.ClientManager
import com.unipi.dii.sonicroutes.model.Edge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class RouteFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentRouteBinding
    private var map: GoogleMap? = null
    private var fileName: String? = null // received as argument
    private val minAmplitude = 0.0 // this and the following are used to map the amplitude to a color
    private val maxAmplitude = 5000.0
    private lateinit var loadingLayout: FrameLayout


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
                    if (data.size == 5) {
                        try {
                            val edge = Edge(
                                data[0].trim().toInt(),
                                data[1].trim().toInt(),
                                data[2].trim().toDouble(),
                                data[3].trim().toInt()
                            )
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


    /**
     * Adds a point to the polyline on the map.
     *
     * This function takes an Edge object as input, which contains the IDs of the starting and ending crossings.
     * It makes asynchronous network calls to fetch the coordinates of these crossings from the server.
     * Once the coordinates are fetched, it adds a line segment to the polyline on the map.
     * The line segment is drawn between the starting and ending points of the edge.
     *
     * @param edge The Edge object containing the IDs of the starting and ending crossings.
     */
    private fun addPointToPolyline(edge: Edge) {

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val startingCrossing = withContext(Dispatchers.IO) {
                    context?.let { ClientManager(requireContext()).getCrossingCoordinates(it,edge.getStartCrossingId()) }
                }
                val endingCrossing = withContext(Dispatchers.IO) {
                    context?.let { ClientManager(requireContext()).getCrossingCoordinates(it,edge.getEndCrossingId()) }
                }

                // Define a pattern for the polyline
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
                endingCrossing?.let { CameraUpdateFactory.newLatLngZoom(it, 16f) }
                    ?.let { map?.moveCamera(it) }
            } catch (e: IOException) {
                // Handle the I/O error here
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun mapAmplitudeToColor(amplitude: Double): Int {
        if (amplitude > maxAmplitude)
            return Color.RED
        // Simple linear mapping
        Log.e(TAG, "Amplitude: $amplitude")
        val normalizedAmplitude = (amplitude - minAmplitude) / (maxAmplitude - minAmplitude)
        Log.e(TAG, "Normalized amplitude: $normalizedAmplitude")
        return Color.rgb(
            (normalizedAmplitude * 255).toInt(),
            ((1 - normalizedAmplitude) * 255).toInt(),
            0
        )
    }

}

