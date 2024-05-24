package com.unipi.dii.sonicroutes.ui.tracking

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.databinding.FragmentHomeBinding
import com.unipi.dii.sonicroutes.databinding.ItemFileBinding
import com.unipi.dii.sonicroutes.network.ClientManager
import com.unipi.dii.sonicroutes.model.Crossing
import com.unipi.dii.sonicroutes.model.Edge
import com.unipi.dii.sonicroutes.model.Route
import com.unipi.dii.sonicroutes.navigation.NavigationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapFragment : Fragment(), OnMapReadyCallback, SearchResultClickListener{
    private lateinit var binding: FragmentHomeBinding
    private lateinit var navigationManager: NavigationManager
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var routeReceived = false
    private lateinit var userLocation: LatLng
    private val markers = ArrayList<Crossing>()
    private val route = ArrayList<Edge>() // contiene gli edge tra i checkpoint, serve per ricostruire il percorso ed avere misura del rumore
    private var cumulativeNoise = 0.0 // tiene conto del rumore cumulativo in un edge (percorso tra due checkpoint)
    private var numberOfMeasurements = 0 // tiene conto del numero di misurazioni effettuate in un edge
    private var lastCheckpoint: Crossing? = null // contiene l'ultimo checkpoint visitato, serve per capire se si è in un nuovo checkpoint
    private lateinit var searchView: SearchView
    private var isMapMovedByUser = false
    private var emptyFile = true
    private lateinit var startRecordingButton : Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)

        if (markers.isEmpty())
            fetchAllCrossings() // fetch all crossings from the server
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        startRecordingButton = binding.startRecordingButton
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        changeButtonColor(startRecordingButton)
        changeButtonVisibility(startRecordingButton)
        startRecordingButton.setOnClickListener { toggleRecording(startRecordingButton) }

        // Check and request GPS enablement if not enabled
        checkAndPromptToEnableGPS()
        searchView = binding.searchView
        // disabilito la search view fintanto che la posizione utente non è pronta
        searchView.isEnabled = false

        val recyclerView = binding.searchResultsRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        val textViewNotFound = binding.textViewNotFound

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { query ->
                    val filteredMarkers = markers.filter { crossing ->
                        crossing.getStreetName().any { street ->
                            street.contains(query, ignoreCase = true)
                        }
                    }
                    if (filteredMarkers.isEmpty()) {
                        textViewNotFound.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        textViewNotFound.visibility = View.GONE
                        recyclerView.adapter =
                            SearchResultAdapter(filteredMarkers, query, userLocation, searchView, this@MapFragment)
                        recyclerView.visibility =
                            if (query.isNotEmpty()) View.VISIBLE else View.GONE

                    }
                }
                return true
            }
        })
    }

    private fun checkAndPromptToEnableGPS() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // GPS is not enabled, show dialog to enable it
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Il GPS è disabilitato. \nSi prega di abilitare il GPS per utilizzare l'applicazione.")
            builder.setPositiveButton("Abilita") { _, _ ->
                // Open GPS settings screen
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            builder.setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        checkPermissionsAndSetupMap()
        // Add a listener for when the user moves the map
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isMapMovedByUser = true
            }
        }

        // Add a listener for when the user clicks the button to center the map on their location
        map.setOnMyLocationButtonClickListener {
            isMapMovedByUser = false
            false // Return false to let the map handle the click and center on the user's location
        }
    }

    private fun fetchAllCrossings() {
        val clientManager = ClientManager(requireContext())
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val crossings =
                    context?.let { clientManager.getCrossings(it,"pisa") }
                markers.clear()
                if (crossings != null) {
                    markers.addAll(crossings)
                }

            } catch (e: IOException) {
                // Handle the I/O error here
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndSetupMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            print("Permission not granted")
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupMap()
            searchView.isEnabled = true
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupMap()
        } else {
            showMessageToUser("Per favore, concedere il permesso per la localizzazione.")
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startNoiseRecording()
        } else {
            showMessageToUser("Per favore, concedere il permesso per la registrazione audio.")
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
        val locationRequest = LocationRequest.Builder(3000)
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

        if(!isMapMovedByUser) {
            //map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f))

            val cameraPosition = CameraPosition.Builder()
                .target(userLocation)
                .zoom(17f)
                .bearing(location.bearing) // Orient the map in the direction of the user's movement
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    override fun onResume() {
        super.onResume()
        if(isRecording)
            checkPermissionsAndSetupRecording()
    }

    private fun checkPermissionsAndSetupRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                        handler.postDelayed(this, 3000)
                    }
                }
            }, 3000)
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception during audio recording setup: ${e.message}")
        }
    }

    private fun findNearestMarker(userLocation: LatLng, markerList: ArrayList<Crossing>): LatLng? {
        var nearestMarker: Crossing? = null
        var minDistance = Double.MAX_VALUE

        for (marker in markerList) {
            val distance = calculateDistance(userLocation, marker.getCoordinates())
            if (distance < minDistance) {
                minDistance = distance
                nearestMarker = marker
            }
        }

        var contains = false
        // controllo se lastCheckpoint contiene almeno una street in comune con nearestmarker
        if (lastCheckpoint!=null && nearestMarker != null && lastCheckpoint!!.getId() != nearestMarker.getId()) {
            for (name in lastCheckpoint!!.getStreetName()) {
                if (nearestMarker.getStreetName().contains(name)) {
                    contains = true // essentially, this is saying that we are in a new checkpoint
                    break
                }
            }
        }
        if (minDistance < 40 && (lastCheckpoint == null || contains)) { // se entro qui sono in nuovo checkpoint
            if(lastCheckpoint!=null) { // se non sono al primo checkpoint, allora creo un edge tra il nuovo checkpoint ed il precedente
                val edge = Edge(lastCheckpoint!!.getId(), nearestMarker!!.getId(), cumulativeNoise, numberOfMeasurements)
                route.add(edge)

                // reset delle variabili per il prossimo checkpoint
                cumulativeNoise = 0.0
                numberOfMeasurements = 0
                // invio l'edge al server
                ClientManager(requireContext()).uploadEdge(edge)
                // scrivo sul log locale
                val file = File(context?.filesDir, "TMP")
                try {
                    FileOutputStream(file, true).use { fos ->
                        OutputStreamWriter(fos).use { writer ->
                            writer.write(edge.toCsvEntry() + "\n")
                            emptyFile = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to write data to file", e)
                }

            }
            if (nearestMarker != null) {
                lastCheckpoint = nearestMarker
                return nearestMarker.getCoordinates()
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

            if (lastCheckpoint!=null) { // sound not recorded until at least one checkpoint is reached
                val amplitude = audioData.maxOrNull()?.toInt() ?: 0
                // if amplitude < 0 or > 30000
                if (amplitude in 0..30000) {
                    cumulativeNoise += amplitude
                    numberOfMeasurements++
                }
            }

            findNearestMarker(userLocation, markers)?.let { nearestMarker ->

                // Aggiungi il marker più vicino alla mappa con un colore diverso
                map.addMarker(
                    MarkerOptions()
                        .position(nearestMarker)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )

            }

        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            try {
                audioRecord?.stop() // Ferma la registrazione
                audioRecord?.release() // Rilascia le risorse dell'oggetto AudioRecord
                isRecording = false // Imposta lo stato di registrazione su falso
                routeReceived = false
                changeButtonVisibility(startRecordingButton)

            } catch (e: SecurityException) {
                Log.e("HomeFragment", "Security Exception during audio recording stop: ${e.message}")
            }
        }
    }

    private fun showMessageToUser(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("ATTENZIONE!")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                if(message.contains("localizzazione")) {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                else {
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            .create()
            .show()
    }

    private fun toggleRecording(startRecordingButton: Button) {
        isRecording = !isRecording
        if (isRecording) {
            startRecordingButton.text = getString(R.string.stop_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            checkPermissionsAndSetupRecording()
            // Add markers to the map
            for (marker in markers) {
                map.addMarker(MarkerOptions().position(marker.getCoordinates()))
            }

            createTMPFile()

        } else {
            stopRecording()
            startRecordingButton.text = getString(R.string.start_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_purple))
            map.clear()
            lastCheckpoint = null
            // if emptyFile, cancello il file
            if(emptyFile) {
                val file = File(context?.filesDir, "TMP")
                file.delete()
            }
            commitFile()
            emptyFile = true
        }
    }

    /**
     * This function is responsible for creating a temporary file that will be used to store data during the recording session.
     * The function first deletes any existing temporary files to ensure a clean start.
     * Then, it creates a new temporary file and writes the header line for the data to be stored.
     * The data includes the start and end IDs of a crossing and the amplitude and number of measurements of the noise recorded.
     * The file is named "TMP" and is stored in the application's private file directory.
     * Reason : if user starts recording then goes inside Dashboard, he can see the file even tough he/she still hasn't completed the route
     */
    private fun createTMPFile(){
        // delete all "TMP" files if they exist
        val files = context?.filesDir?.listFiles { file ->
            file.name.startsWith("TMP")
        }
        files?.forEach { file ->
            file.delete()
        }

        val filename = "TMP"
        val filesDir = context?.filesDir
        val file = File(filesDir, filename)
        try {
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write("startCrossingId,endCrossingId,amplitude,measurements,city\n")
                    //todo : jacopo dice qui sopra di salvare solo strt ed end id e poi recuperare il rumore medio dal server (ci sta)
                }
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to write data to file", e)
        }
    }

    /**
     * This function is responsible for committing the temporary file to a permanent file.
     * The function changes the name of the temporary file to a new file with a timestamp.
     * The timestamp is generated using the current date and time when the route is ended.
     * The new file is named "data_yyyyMMdd_HHmmss.csv" and is stored in the application's private file directory.
     */
    private fun commitFile(){
        // function to change the file with "filename" to "TMP"
        val file = File(context?.filesDir, "TMP")
        val filenamePrefix = "data_"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // Costruisci il nome del nuovo file utilizzando il timestamp attuale
        val newFilename = "$filenamePrefix$timestamp.csv"
        file.renameTo(File(context?.filesDir, newFilename))
    }

    private fun changeButtonColor(startRecordingButton: Button) {
        if(!isRecording) {
            startRecordingButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.holo_purple
                )
            )
            startRecordingButton.text = getString(R.string.start_recording)
        }else {
            startRecordingButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.holo_red_light
                )
            )
            startRecordingButton.text = getString(R.string.stop_recording)
        }
    }

    private fun changeButtonVisibility(startRecordingButton: Button) {
        if(isRecording || routeReceived) {
            startRecordingButton.visibility = View.VISIBLE
        }else {
            startRecordingButton.visibility = View.GONE
        }
    }

    // function that takes a route and shows it on the map

    override fun onSearchResultClicked(route: Route) {
        map.clear()
        navigationManager = NavigationManager(map)
        navigationManager.showRouteOnMap(route)

        if(routeReceived && isRecording){
            isRecording = false
            changeButtonColor(startRecordingButton)
        }
        routeReceived = true
        changeButtonVisibility(startRecordingButton)
    }
}