package com.unipi.dii.sonicroutes.ui.home

import GeocodingUtil
import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.model.Apis
import com.unipi.dii.sonicroutes.model.Crossing
import com.unipi.dii.sonicroutes.model.Edge
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var userLocation: LatLng
    private val deviceId = UUID.randomUUID().toString()
    private lateinit var geocodingUtil: GeocodingUtil
    private val markers = ArrayList<Crossing>()
    private var lastCheckpoint: Crossing? = null // contiene l'ultimo checkpoint visitato, serve per capire se si è in un nuovo checkpoint
    private val route = ArrayList<Edge>() // contiene gli edge tra i checkpoint, serve per ricostruire il percorso ed avere misura del rumore
    private var cumulativeNoise = 0.0 // tiene conto del rumore cumulativo in un edge (percorso tra due checkpoint)
    private var numberOfMeasurements = 0 // tiene conto del numero di misurazioni effettuate in un edge


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        val startRecordingButton = view.findViewById<View>(R.id.startRecordingButton)
        if(!isRecording) {
            startRecordingButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.holo_green_dark
                )
            )
        }else {
            startRecordingButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.holo_red_light
                )
            )
            (startRecordingButton as Button).text = getString(R.string.stop_recording)
        }
        startRecordingButton.setOnClickListener { toggleRecording(startRecordingButton) }
            geocodingUtil = GeocodingUtil(requireContext())
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        checkPermissionsAndSetupMap()
        addMarkersFromCSV() // Add markers from CSV when the map is ready
    }


    private fun addMarkersFromCSV() {
        try {
            val inputStream = resources.openRawResource(R.raw.intersections_clustered)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            reader.readLine() // Skip the header
            while (reader.readLine().also { line = it } != null) {
                val columns = line!!.split(",")
                val id = columns[0].toInt()
                val latitude = columns[1].toDouble()
                val longitude = columns[2].toDouble()
                val streetName = columns[3].split(";").map { it.trim() } // Trim each street name
                //todo : inutile o no? direi di sì
                val streetCounter = columns[4].toInt() // street counter is at index 4

                // Create a POI object with latitude, longitude, and street name
                val poi = Crossing(id, latitude, longitude, streetName)

                // Add the POI object to the markers list
                markers.add(poi)
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error adding markers from CSV: ${e.message}")
        }
    }


    private fun checkPermissionsAndSetupMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupMap()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupMap()
        } else {
            showMessageToUser("Per favore, concedere il permesso per la localizzazione.")
        }
    }

    private fun setupMap() {

        try {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            startLocationUpdates()
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(5000)
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

        try {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception while requesting location updates: ${e.message}")
        }
    }

    private fun updateMap(location: Location) {
        if(location.latitude !=0.0){
            userLocation = LatLng(location.latitude, location.longitude)
        }

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f))
        geocodingUtil.getAddressFromLocation(location.latitude, location.longitude) { address ->
            println(address)
            // todo : manipolare address
        }
    }

    override fun onResume() {
        super.onResume()
        if(isRecording)
            checkPermissionsAndSetupRecording()
    }

    private fun checkPermissionsAndSetupRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startNoiseRecording()
        }
    }

    private fun startNoiseRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build())
                .setBufferSizeInBytes(minBufferSize)
                .build()

            audioRecord?.startRecording()

            val audioData = ShortArray(minBufferSize)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (isRecording) {
                        val readResult = audioRecord?.read(audioData, 0, minBufferSize, AudioRecord.READ_BLOCKING) ?: -1
                        if (readResult >= 0) {
                            processRecordingData(audioData)
                        } else {
                            Log.e("HomeFragment", "Error reading audio data")
                        }
                        handler.postDelayed(this, 5000)
                    }
                }
            }, 5000)
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception during audio recording setup: ${e.message}")
        }
    }

    private fun findNearestMarker(userLocation: LatLng, markerList: ArrayList<Crossing>): LatLng? {
        var nearestMarker: Crossing? = null
        var minDistance = Double.MAX_VALUE

        for (marker in markerList) {
            val distance = calculateDistance(userLocation, marker.getLatLng())
            if (distance < minDistance) {
                minDistance = distance
                nearestMarker = marker
            }
        }
        Log.d("HomeFragment", "Distance: $minDistance")
        var contains = false
        // controllo se route.last.getStreetname() contiene almeno una street in comune con nearestmarker
        if (lastCheckpoint!=null && nearestMarker != null && lastCheckpoint!!.getCrossingId() != nearestMarker.getCrossingId()) {
            for (name in lastCheckpoint!!.getStreetName()) {
                if (nearestMarker.getStreetName().contains(name)) {
                    contains = true // essentially, this is saying that we are in a new checkpoint
                    break
                }
            }
        }
        if (minDistance < 40 && (lastCheckpoint == null || contains)) { // se entro qui sono in nuovo checkpoint
            if(lastCheckpoint!=null) { // se non sono al primo checkpoint, allora creo un edge tra il nuovo checkpoint ed il precedente
                val edge = Edge(lastCheckpoint!!.getCrossingId(), nearestMarker!!.getCrossingId(), cumulativeNoise, numberOfMeasurements)
                route.add(edge)
                // stampo l'edge per debug
                Log.d("HomeFragment", "Edge: $edge")
                // reset delle variabili per il prossimo checkpoint
                cumulativeNoise = 0.0
                numberOfMeasurements = 0
                // invio l'edge al server
                Apis(requireContext()).uploadEdge(edge)
            }
            if (nearestMarker != null) {
                lastCheckpoint = nearestMarker
                return nearestMarker.getLatLng()
            }
        }
        return null
    }

    private fun calculateDistance(location1: LatLng, location2: LatLng): Double {
        val radius = 6371.0 // Raggio della Terra in chilometri
        val latDistance = Math.toRadians(location2.latitude - location1.latitude)
        val lonDistance = Math.toRadians(location2.longitude - location1.longitude)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(location1.latitude)) * cos(Math.toRadians(location2.latitude)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radius * c * 1000 // Converti in metri
    }

    private fun processRecordingData(audioData: ShortArray) {
        if (isRecording &&::userLocation.isInitialized && audioData.isNotEmpty()) {
            val amplitude = audioData.maxOrNull()?.toInt() ?: 0
            Log.d("HomeFragment", "Current Noise Level: $amplitude")
            val jsonEntry = Gson().toJson(
                NoiseData(
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude,
                    amplitude = amplitude,
                    timestamp = System.currentTimeMillis(),
                    deviceId = deviceId
                )
            )

            cumulativeNoise += amplitude
            numberOfMeasurements++
            Log.d("HomeFragment", "Cumulative Noise: $cumulativeNoise")
            Log.d("HomeFragment", "Number of Measurements: $numberOfMeasurements")
            Log.d("HomeFragment", "JSON Entry: $jsonEntry")

            findNearestMarker(userLocation, markers)?.let { nearestMarker ->
                Log.d("HomeFragment", "Nearest Marker: $nearestMarker")

                // Aggiungi il marker più vicino alla mappa con un colore diverso
                map.addMarker(
                    MarkerOptions()
                        .position(nearestMarker)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

           // scrivi i dati su file
            val filename = "data.json"
            val file = File(context?.filesDir, filename)
            try {
                FileWriter(file, true).use { writer ->
                    writer.write(jsonEntry + "\n")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to write data to file", e)
            }

        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            try {
                audioRecord?.stop() // Ferma la registrazione
                audioRecord?.release() // Rilascia le risorse dell'oggetto AudioRecord
                isRecording = false // Imposta lo stato di registrazione su falso
            } catch (e: SecurityException) {
                Log.e("HomeFragment", "Security Exception during audio recording stop: ${e.message}")
            }
        }
    }

    private fun showMessageToUser(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("ATTENZIONE!")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    data class NoiseData(val latitude: Double, val longitude: Double, val amplitude: Int, val timestamp: Long, val deviceId: String)

    private fun toggleRecording(startRecordingButton: View) {
        isRecording = !isRecording
        if (isRecording) {
            (startRecordingButton as Button).text = getString(R.string.stop_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            checkPermissionsAndSetupRecording()
            // Add markers to the map
            for (marker in markers) {
                map.addMarker(MarkerOptions().position(marker.getLatLng()))
            }
        } else {
            stopRecording()
            (startRecordingButton as Button).text = getString(R.string.start_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            map.clear()
            lastCheckpoint = null
        }
    }
}
