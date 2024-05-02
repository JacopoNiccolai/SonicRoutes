package com.unipi.dii.sonicroutes.ui.dashboard
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class DashboardViewModel : ViewModel() {
    private val _text = MutableLiveData<String>()
    val text: LiveData<String> = _text

    fun loadData(context: Context) {
        val fileName = "data.json"
        val file = File(context.getExternalFilesDir(null), fileName)
        if (file.exists()) {
            val rawJsonData = file.readText()
            // Convert the sequence of JSON objects into a valid JSON array
            val validJsonArray = "[${rawJsonData.trim().replace("\n", ",")}]"
            val gson = Gson()
            val type = object : TypeToken<List<NoiseData>>() {}.type
            val data: List<NoiseData> = gson.fromJson(validJsonArray, type)
            _text.value = data.joinToString("\n") {
                "Time: ${it.timestamp}, Lat: ${it.latitude}, Long: ${it.longitude}, Amp: ${it.amplitude}, Device: ${it.deviceId}"
            }
        } else {
            _text.value = "No data available"
        }
    }

    data class NoiseData(
        val latitude: Double,
        val longitude: Double,
        val amplitude: Int,
        val timestamp: Long,
        val deviceId: String
    )
}
