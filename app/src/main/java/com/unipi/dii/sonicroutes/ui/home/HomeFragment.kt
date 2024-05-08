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
import android.widget.Toast
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
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.ui.network.ServerApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileWriter
import java.util.UUID

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var userLocation: LatLng
    private val deviceId = UUID.randomUUID().toString()
    private lateinit var geocodingUtil: GeocodingUtil

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        val startRecordingButton = view.findViewById<View>(R.id.startRecordingButton)
        startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        startRecordingButton.setOnClickListener { toggleRecording(startRecordingButton) }
            geocodingUtil = GeocodingUtil(requireContext())
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        checkPermissionsAndSetupMap()
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
    /*
    finche via uguale
        somma_rum += rumore_rilevato
        num_oss++
    quando via diversa
        rumore_medio = somma_rum/num_oss
    */

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

            Log.d("HomeFragment", "JSON Entry: $jsonEntry")

            // send the json entry to the server
            val retrofit = Retrofit.Builder()
                .baseUrl("http://10.1.1.22:5000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val serverApi = retrofit.create(ServerApi::class.java)

            val jsonElement = JsonParser.parseString(jsonEntry)
            val jsonObject = jsonElement.asJsonObject
            val call = serverApi.uploadDataJson(jsonObject)

            // Commend API call
            call.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        // todo : rimuovi, altrimenti ogni volta l'utente vede un toast
                        Toast.makeText(requireContext(), "JSON entry sent correctly", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send JSON entry", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    t.printStackTrace()
                }
            })

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

    override fun onPause() {
        super.onPause()
        stopRecording()
        if (::fusedLocationProviderClient.isInitialized) {
            try {
                fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            } catch (e: SecurityException) {
                Log.e("HomeFragment", "Security Exception while removing location updates: ${e.message}")
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
        } else {
            stopRecording()
            (startRecordingButton as Button).text = getString(R.string.start_recording)
            startRecordingButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        }
    }
}
