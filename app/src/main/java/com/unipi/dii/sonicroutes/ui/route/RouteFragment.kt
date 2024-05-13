package com.unipi.dii.sonicroutes.ui.route

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.model.Edge
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class RouteFragment : Fragment(), OnMapReadyCallback {

    private var map: GoogleMap? = null
    private var fileName: String? = null // received as argument

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get filename from arguments
        fileName = arguments?.getString("fileName")?.trim()
        Log.d(TAG, "File name: $fileName")
        val mapFragment = childFragmentManager.findFragmentById(R.id.route_fragment_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        view.findViewById<Button>(R.id.button_back_to_dashboard).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
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
                    if (data.size == 4) {
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
            } catch (e: Exception) {
                Log.e(TAG, "Error reading file: ${e.message}")
                // Print a more detailed log
                e.printStackTrace()
            }
        }
    }


    private fun addPointToPolyline(edge: Edge) {
        // Utilizza i dati dell'edge per aggiungere un punto alla Polyline sulla mappa
        // Ad esempio, puoi usare le coordinate degli incroci per aggiungere un punto alla Polyline
        // Qui hai bisogno di un metodo per mappare gli incroci a coordinate LatLng
        // E poi aggiungi queste coordinate alla Polyline
        Log.d(TAG, "edge: $edge")
    }
}

