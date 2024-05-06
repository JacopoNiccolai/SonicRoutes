
import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.io.IOException
import java.util.Locale

class GeocodingUtil(private val context: Context) {

    fun getAddressFromLocation(latitude: Double, longitude: Double, callback: (String?) -> Unit) {
        val geocoder = Geocoder(context, Locale.getDefault())

        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addressText = addresses[0].getAddressLine(0)
                callback(addressText)
                println(addressText)
            } else {
                callback(null)
            }
        } catch (e: IOException) {
            Log.e("GeocodingUtil", "Error getting address from location", e)
            callback(null)
        }
    }
}
