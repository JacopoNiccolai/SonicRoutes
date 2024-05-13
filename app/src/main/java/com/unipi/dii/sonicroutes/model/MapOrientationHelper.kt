import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MapOrientationHelper(private val context: Context, private val map: GoogleMap) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    // Sensor readings
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)

    private var lastUpdateTime: Long = 0
    private val updateInterval = 5000 // Update interval in milliseconds (e.g., 5 seconds)

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun startListening() {
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, event.values.size)
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, event.values.size)
        }

        // Check if enough time has passed since the last update
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateInterval) {
            updateOrientation()
            lastUpdateTime = currentTime
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun updateOrientation() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationValues)

        val azimuthRadians = orientationValues[0] // azimuth in radians
        val azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat() // azimuth in degrees

        // Rotate the map
        val currentCameraPosition = map.cameraPosition
        val newCameraPosition = CameraPosition.Builder(currentCameraPosition)
            .bearing(-azimuthDegrees) // Rotate map based on azimuth (negative to match device orientation)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
    }
}
