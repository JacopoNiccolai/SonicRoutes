package com.unipi.dii.sonicroutes.ui.home

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Looper
import android.util.Log
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
import com.google.gson.Gson
import com.unipi.dii.sonicroutes.R
import java.io.File
import java.io.FileWriter

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var userLocation: LatLng


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
    }

    private val requestPermissionLauncherMap = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Se il permesso è stato concesso, avvia la configurazione della mappa
            setupMap()
        } else {
            // Se il permesso è stato negato mostra un messaggio all'utente
            showMessageToUser("Per favore, concedere il permesso per la localizzazione.")
        }
    }

    private fun setupMap() {
        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncherMap.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Se hai i permessi, configura la mappa
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        setupRecording()
    }

    private fun setupRecording() {
        // Controlla se hai i permessi necessari
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Se non hai il permesso, richiedilo all'utente
            requestPermissionLauncherRecording.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Se hai i permessi, inizia a registrare il rumore
            startNoiseRecording()
        }
    }

    private val requestPermissionLauncherRecording = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Se il permesso è stato concesso, avvia la registrazione
            startNoiseRecording()
        } else {
            // Se il permesso è stato negato mostra un messaggio all'utente
            showMessageToUser("Per favore, concedere il permesso per registrare l'audio.")
        }
    }

    private fun startNoiseRecording() {
        // Controlla se hai il permesso RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Hai il permesso, quindi puoi avviare la registrazione
            val sampleRate = 44100  // Frequenza di campionamento comune per l'audio
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
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

                            val jsonEntry = Gson().toJson(
                                NoiseData(
                                    latitude = userLocation.latitude,
                                    longitude = userLocation.longitude,
                                    amplitude = amplitude,
                                    timestamp = System.currentTimeMillis(),
                                    deviceId = "1"
                                )
                            )

                            println("JSON Entry: $jsonEntry")

                            val filename = "data.json"
                            val file = File(context?.getExternalFilesDir(null), filename)
                            FileWriter(file, true).use { writer ->
                                writer.write(jsonEntry + "\n")
                                Log.d("HomeFragment", "Dati scritti nel file: $filename")
                            }
                        }
                        handler.postDelayed(this, 5000) // aggiorna ogni 5 secondi
                    }
                }
            }, 5000)
        } else {
            // Il permesso RECORD_AUDIO non è stato concesso dall'utente
            showMessageToUser("Per favore, concedere il permesso per registrare l'audio.")
        }
    }

    data class NoiseData(
        val latitude: Double,
        val longitude: Double,
        val amplitude: Int,
        val timestamp: Long,
        val deviceId: String
    )

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
        userLocation = LatLng(location.latitude, location.longitude)
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

    private fun showMessageToUser(message: String) {
        // Puoi scegliere come mostrare il messaggio all'utente, ad esempio utilizzando un dialog
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle("ATTENZIONE!")
        alertDialogBuilder.setMessage(message)
        alertDialogBuilder.setPositiveButton("OK") { dialog, _ ->
            // Puoi implementare qui azioni aggiuntive se necessario
            dialog.dismiss()
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

}