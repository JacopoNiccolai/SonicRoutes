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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.unipi.dii.sonicroutes.MainActivity
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.databinding.FragmentHomeBinding
import com.unipi.dii.sonicroutes.model.Crossing
import com.unipi.dii.sonicroutes.model.Edge
import com.unipi.dii.sonicroutes.model.Route
import com.unipi.dii.sonicroutes.navigation.NavigationManager
import com.unipi.dii.sonicroutes.network.ClientManager
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
    private lateinit var userLocation: LatLng // user's location
    private val markers = ArrayList<Crossing>() // list of nodes, initialized as empty
    private val route = ArrayList<Edge>() // list of edges, used to build the route and store the noise level
    private var cumulativeNoise = 0.0 // noise level in an edge
    private var numberOfMeasurements = 0 // number of measurements of the noise in an edge
    private var lastCheckpoint: Crossing? = null // last visited node(checkpoint), used to check if we are in a new checkpoint
    private lateinit var searchView: SearchView
    private var isMapMovedByUser = false
    private var emptyFile = true // flag to check if the file is empty, in that case the temporary file is discarded
    private lateinit var startRecordingButton : Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)

        if (markers.isEmpty()) {
            fetchAllCrossings() // fetch all crossings from the server
            showMessageToUser("Welcome to SonicRoutes, the app for finding the quietest route to navigate your city! Search for your destination in the top bar and enjoy the journey!", "Welcome!")
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize the recording button and assign it to the startRecordingButton variable
        startRecordingButton = binding.startRecordingButton

        // Call the super.onViewCreated() function to execute the parent class's code
        super.onViewCreated(view, savedInstanceState)

        // Get a reference to the SupportMapFragment instance and request the map to be loaded asynchronously
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        // Call functions to change the color and visibility of the recording button
        changeButtonColor(startRecordingButton)
        changeButtonVisibility(startRecordingButton)

        // Set a listener on the recording button to start or stop recording when it is pressed
        startRecordingButton.setOnClickListener { toggleRecording(startRecordingButton) }

        // Check and request GPS enablement if it is not enabled
        checkAndPromptToEnableGPS()

        // Initialize the SearchView
        searchView = binding.searchView

        // Disable the SearchView until the user's location is ready
        searchView.isEnabled = false

        // Set up the RecyclerView to display search results
        val recyclerView = binding.searchResultsRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Set up a TextView to display a message when there are no search results
        val textViewNotFound = binding.textViewNotFound

        // Set a listener on the SearchView to filter
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

    // Function that checks if the GPS is enabled and prompts the user to enable it if it is not
    private fun checkAndPromptToEnableGPS() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // GPS is not enabled, show dialog to enable it
            showMessageToUser("GPS is disabled.\nPlease enable GPS to use the app.", "WARNING!")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        checkPermissionsAndSetupMap()   //check if permissions are okay
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

    // Function that fetches all crossings from the server
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

    // Function that checks if the user has granted the necessary permissions and sets up the map
    private fun checkPermissionsAndSetupMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            print("Permission not granted")
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)  //ask for localization permission
        } else {
            setupMap()
            searchView.isEnabled = true
        }
    }

    // Request location permission
    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupMap()
        } else {
            showMessageToUser("Please grant permission for location access.", "WARNING!")
        }
    }

    // Request audio permission
    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startNoiseRecording()
        } else {
            showMessageToUser("Please grant permission for audio recording.", "WARNING!")
        }
    }

    // Function that sets up the map using the user's location
    private fun setupMap() {
        try {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            startLocationUpdates()  //start sampling position
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception: ${e.message}")
        }
    }

    // Function that starts updating the user's location
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(3000) //every 3 seconds start updating position
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

    // Function that updates the map with the user's location
    private fun updateMap(location: Location) {
        if(location.latitude !=0.0){    //first time
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

    // Function that checks if the user has granted the necessary permissions and starts the noise recording
    private fun checkPermissionsAndSetupRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startNoiseRecording()
        }
    }

    // Function that starts recording the noise
    private fun startNoiseRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try { //start noise sampling
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
            }, 3000) // sample every 3 seconds
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Security Exception during audio recording setup: ${e.message}")
        }
    }

    // Function that finds the nearest marker to the user's location
    private fun findNearestMarker(userLocation: LatLng, markerList: ArrayList<Crossing>): LatLng? {
        var nearestMarker: Crossing? = null
        var minDistance = Double.MAX_VALUE
        // look for the nearest marker
        for (marker in markerList) {
            val distance = calculateDistance(userLocation, marker.getCoordinates())
            if (distance < minDistance) {   //test if I'm near enough to the marker
                minDistance = distance
                nearestMarker = marker
            }
        }

        var contains = false
        // check if lastCheckpoint has at least one street name in common with the nearestMarker
        // if not that edge doesn't exist
        if (lastCheckpoint!=null && nearestMarker != null && lastCheckpoint!!.getId() != nearestMarker.getId()) {
            for (name in lastCheckpoint!!.getStreetName()) {
                if (nearestMarker.getStreetName().contains(name)) {
                    contains = true // essentially, this is saying that we are in a new checkpoint
                    break
                }
            }
        }
        if (minDistance < 40 && (lastCheckpoint == null || contains)) { // if I'm near enough to the marker
            if(lastCheckpoint!=null) { // if lastCheckpoint is not null (first checkpoint), I'm in a new checkpoint
                // create an edge between the lastCheckpoint and the nearestMarker
                val edge = Edge(lastCheckpoint!!.getId(),lastCheckpoint!!.getCoordinates(), nearestMarker!!.getId(),nearestMarker.getCoordinates(), cumulativeNoise, numberOfMeasurements)
                route.add(edge)

                // reset the noise level and the number of measurements
                cumulativeNoise = 0.0
                numberOfMeasurements = 0
                // upload the edge to the server
                ClientManager(requireContext()).uploadEdge(edge)
                // write the edge to the temporary file
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

    // Function that calculates the distance between two locations
    private fun calculateDistance(location1: LatLng, location2: LatLng): Double {
        val radius = 6371.0 // Earth radius in km
        val latDistance = Math.toRadians(location2.latitude - location1.latitude)
        val lonDistance = Math.toRadians(location2.longitude - location1.longitude)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(location1.latitude)) * cos(Math.toRadians(location2.latitude)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radius * c * 1000 // convert to meters
    }

    // Function that processes the noise data recorded
    private fun processRecordingData(audioData: ShortArray) {
        if (isRecording &&::userLocation.isInitialized && audioData.isNotEmpty()) {
            // calculate the amplitude of the noise
            if (lastCheckpoint!=null) { // sound not recorded until at least one checkpoint is reached
                val amplitude = audioData.maxOrNull()?.toInt() ?: 0
                // if amplitude < 0 or > 30000
                if (amplitude in 0..30000) { // if the amplitude is in the correct range
                    cumulativeNoise += amplitude
                    numberOfMeasurements++
                }
            }

            findNearestMarker(userLocation, markers)?.let { nearestMarker ->

                // Add a marker to the map to indicate the nearest crossing
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
                audioRecord?.stop() // stop recording
                audioRecord?.release() // release the resources
                isRecording = false // change the recording state
                routeReceived = false
                changeButtonVisibility(startRecordingButton)

            } catch (e: SecurityException) {
                Log.e("HomeFragment", "Security Exception during audio recording stop: ${e.message}")
            }
        }
    }

    // Function that shows a message to the user, if the message is about location permission or
    // audio permission, it will ask the user to grant the permission
    private fun showMessageToUser(message: String, title: String) {
        AlertDialog.Builder(requireContext())

            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                if(title=="WARNING!"){
                    if(message.contains("location")) {
                        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    else if(message.contains("audio")) {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    else if(message.contains("GPS")) {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            }
            .setNegativeButton("Close the app") { _, _ ->
                MainActivity.instance?.finishAffinity()
            }
            .create()
            .show()
    }

    // Function that toggles the recording state
    private fun toggleRecording(startRecordingButton: Button) {
        //change recording state
        isRecording = !isRecording
        if (isRecording) {
            startRecordingButton.text = getString(R.string.stop_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            checkPermissionsAndSetupRecording()
            // Add markers to the map
            for (marker in markers) {
                map.addMarker(MarkerOptions().position(marker.getCoordinates()))
            }

            createTMPFile() // create a temporary file to store the data

        } else {
            stopRecording()
            startRecordingButton.text = getString(R.string.start_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_purple))
            map.clear()
            lastCheckpoint = null
            // if emptyFile, file is deleted
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
                    writer.write("startCrossingId,startLatLng,endCrossingId,endLatLng,amplitude,measurements,city\n")
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
        // create a new file named "data_yyyyMMdd_HHmmss.csv"
        val newFilename = "$filenamePrefix$timestamp.csv"
        file.renameTo(File(context?.filesDir, newFilename))
    }

    // the color is changed based on the recording state
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

    // the button is visible only if the route is received
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
