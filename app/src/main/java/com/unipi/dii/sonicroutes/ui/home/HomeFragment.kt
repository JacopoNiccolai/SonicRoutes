package com.unipi.dii.sonicroutes.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
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
import com.unipi.dii.sonicroutes.R
import java.io.IOException


class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Se il permesso è stato concesso, avvia la configurazione della mappa
            setupMap()
        } else {
            // Se il permesso è stato negato, gestisci di conseguenza (ad esempio, mostra un messaggio all'utente)
            // Qui puoi gestire il caso in cui l'utente non ha concesso il permesso, ad esempio mostrando un messaggio di avviso
        }
    }

    private fun setupMap() {
        // Controlla se hai i permessi necessari
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else{   // Se hai i permessi, inizia a registrare il rumore
            startNoiseRecording()
        }
        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Se hai i permessi, configura la mappa
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            startLocationUpdates()
        }
    }


    private fun startNoiseRecording() {
        val sampleRate = 44100  // Frequenza di campionamento comune per l'audio
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build())
            .setBufferSizeInBytes(minBufferSize)
            .build()

        audioRecord.startRecording()
        isRecording = true

        val audioData = ShortArray(minBufferSize)

        val handler = android.os.Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val readResult = audioRecord.read(audioData, 0, minBufferSize, AudioRecord.READ_BLOCKING)
                    if (readResult >= 0) {
                        val amplitude = audioData.maxOrNull()?.toInt() ?: 0
                        println("Current Noise Level: $amplitude")
                        /*val jsonEntry = createJsonEntry(
                            latitude = userLocation.latitude,
                            longitude = userLocation.longitude,
                            amplitude = amplitude,
                            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

                        )
                        println(jsonEntry)*/
                    }
                    handler.postDelayed(this, 5000) // aggiorna ogni 5 secondi
                }
            }
        }, 5000)
    }



    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(1000)
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
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f)) // zoom
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
        stopRecording()
        if (::fusedLocationProviderClient.isInitialized) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }
    private fun stopRecording() {
        if (isRecording) {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
        }
    }
}

