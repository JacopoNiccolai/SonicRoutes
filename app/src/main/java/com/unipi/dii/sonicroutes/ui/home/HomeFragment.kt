package com.unipi.dii.sonicroutes.ui.home

import GeocodingUtil
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.model.Apis
import com.unipi.dii.sonicroutes.model.Crossing
import com.unipi.dii.sonicroutes.model.Edge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.unipi.dii.sonicroutes.model.NavigationManager
import com.unipi.dii.sonicroutes.model.Route
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

class HomeFragment : Fragment(), OnMapReadyCallback, SearchResultClickListener{
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var routeReceived = false
    private lateinit var userLocation: LatLng
    private lateinit var geocodingUtil: GeocodingUtil
    private val markers = ArrayList<Crossing>()
    private val route = ArrayList<Edge>() // contiene gli edge tra i checkpoint, serve per ricostruire il percorso ed avere misura del rumore
    private var cumulativeNoise = 0.0 // tiene conto del rumore cumulativo in un edge (percorso tra due checkpoint)
    private var numberOfMeasurements = 0 // tiene conto del numero di misurazioni effettuate in un edge
    private var lastCheckpoint: Crossing? = null // contiene l'ultimo checkpoint visitato, serve per capire se si è in un nuovo checkpoint
    private lateinit var searchView: SearchView
    private var isMapMovedByUser = false
    private var emptyFile = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (markers.isEmpty())
            fetchAllCrossings() // fetch all crossings from the server
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        val startRecordingButton = view.findViewById<View>(R.id.startRecordingButton) as Button
        changeButtonColor(startRecordingButton)
        changeButtonVisibility(startRecordingButton)
        startRecordingButton.setOnClickListener { toggleRecording(startRecordingButton) }
        geocodingUtil = GeocodingUtil(requireContext())

        // Check and request GPS enablement if not enabled
        checkAndPromptToEnableGPS()
        searchView = view.findViewById<SearchView>(R.id.searchView)
        // disabilito la search view fintanto che la posizione utente non è pronta
        searchView.isEnabled = false

        val recyclerView = view.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val textViewNotFound = view.findViewById<TextView>(R.id.textViewNotFound)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // stampo nel log
                Log.d("HomeFragment", "query submitted")
                //todo : probabilmente è meglio non far niente e far si che l'utente clicchi solo un checkpoint
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
                            SearchResultAdapter(filteredMarkers, query, userLocation, searchView, this@HomeFragment)
                        recyclerView.visibility =
                            if (query.isNotEmpty()) View.VISIBLE else View.GONE

                    }
                }
                Log.d("HomeFragment", "End location clicked")
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
        } /*else {
            // GPS is enabled, enable the startRecordingButton
            startRecordingButton.isEnabled = true
        }*/
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
        val apis = Apis(requireContext())
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val crossings =
                    apis.getCrossings("pisa")
                markers.clear()
                markers.addAll(crossings)
                Log.d("HomeFragment", "Fetched ${crossings.size} crossings")
            } catch (e: IOException) {
                // Handle the I/O error here
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndSetupMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupMap()
            searchView.isEnabled = true
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

        if(!isMapMovedByUser) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f))
        }
        geocodingUtil.getAddressFromLocation(location.latitude, location.longitude) { address ->
            //println(address)
            // todo : forse sto address è inutile, ora i controlli sono sulle 'streets' dei crossing
        }

        Log.i("Orsing", map.cameraPosition.bearing.toString())
        //updateCameraBearing(map, map.cameraPosition.bearing)

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
            val distance = calculateDistance(userLocation, marker.getCoordinates())
            if (distance < minDistance) {
                minDistance = distance
                nearestMarker = marker
            }
        }
        Log.d("HomeFragment", "Distance: $minDistance")
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
                // stampo l'edge per debug
                Log.d("HomeFragment", "Edge: $edge")
                // reset delle variabili per il prossimo checkpoint
                cumulativeNoise = 0.0
                numberOfMeasurements = 0
                // invio l'edge al server
                Apis(requireContext()).uploadEdge(edge)
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
                Log.d("HomeFragment", "Current Noise Level: $amplitude")
                cumulativeNoise += amplitude
                numberOfMeasurements++
                Log.d("HomeFragment", "Cumulative Noise: $cumulativeNoise")
                Log.d("HomeFragment", "Number of Measurements: $numberOfMeasurements")
            }

            findNearestMarker(userLocation, markers)?.let { nearestMarker ->
                Log.d("HomeFragment", "Nearest Marker: $nearestMarker")

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
                changeButtonVisibility(view?.findViewById<Button>(R.id.startRecordingButton)!!)

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
                    writer.write("startCrossingId,endCrossingId,amplitude,measurements\n")
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
        // map clear
        map.clear()
        val navigationManager = NavigationManager(map)
        navigationManager.showRouteOnMap(route)
        if(routeReceived && isRecording){
            isRecording = false
            changeButtonColor(view?.findViewById<Button>(R.id.startRecordingButton)!!)
        }
        routeReceived = true
        changeButtonVisibility(view?.findViewById<Button>(R.id.startRecordingButton)!!)
    }

}
